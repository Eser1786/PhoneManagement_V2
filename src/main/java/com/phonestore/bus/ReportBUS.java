package com.phonestore.bus;

import com.phonestore.dao.ProductDAO;
import com.phonestore.dao.ReportDAO;
import com.phonestore.dto.DashboardSummaryDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.RevenuePointDTO;
import com.phonestore.dto.TopProductDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.ProductStatuses;
import com.phonestore.util.SqlErrorMapper;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo thống kê cho màn hình Dashboard và Báo cáo. Cần quyền view_reports
 * (riêng getLowStock cũng cho phép người có quyền manage_inventory).
 *
 * Doanh thu chỉ tính đơn chưa hủy. Biểu đồ theo ngày/tháng luôn đủ mọi điểm trong khoảng
 * (kỳ không có đơn có giá trị 0), nên GUI vẽ thẳng được mà không phải tự điền khoảng trống.
 */
public class ReportBUS {

    /** Phiên bản còn từ ngưỡng này trở xuống được coi là sắp hết hàng. */
    public static final int LOW_STOCK_THRESHOLD = 5;

    /** Khoảng ngày tối đa cho một biểu đồ theo ngày. */
    public static final int MAX_DAY_RANGE = 366;

    public static final int MAX_TOP_LIMIT = 50;

    private final ReportDAO reportDAO;
    private final ProductDAO productDAO;
    private final AuthBUS authBUS;

    public ReportBUS() {
        this(new ReportDAO(), new ProductDAO(), new AuthBUS());
    }

    /** Dùng cho test: truyền DAO giả. */
    public ReportBUS(ReportDAO reportDAO, ProductDAO productDAO, AuthBUS authBUS) {
        this.reportDAO = reportDAO;
        this.productDAO = productDAO;
        this.authBUS = authBUS;
    }

    /** Doanh thu, giá vốn, lợi nhuận từng ngày trong [from, to], đủ mọi ngày (ngày không có đơn là 0). */
    public List<RevenuePointDTO> getRevenueByDay(LocalDate from, LocalDate to) {
        authBUS.requirePermission(PermissionNames.VIEW_REPORTS);
        requireValidRange(from, to);
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAY_RANGE) {
            throw new BusinessException("Chỉ xem được tối đa " + MAX_DAY_RANGE + " ngày mỗi lần, hãy chọn khoảng ngắn hơn");
        }

        Map<String, RevenuePointDTO> byPeriod = new HashMap<>();
        try {
            for (RevenuePointDTO p : reportDAO.revenueByDay(from, to)) {
                byPeriod.put(p.getPeriod(), p);
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải báo cáo doanh thu do lỗi cơ sở dữ liệu");
        }

        List<RevenuePointDTO> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String key = day.toString();
            result.add(byPeriod.getOrDefault(key, RevenuePointDTO.empty(key)));
        }
        return result;
    }

    /** Doanh thu, giá vốn, lợi nhuận từng tháng của một năm: luôn đủ 12 điểm (period dạng yyyy-MM). */
    public List<RevenuePointDTO> getRevenueByMonth(int year) {
        authBUS.requirePermission(PermissionNames.VIEW_REPORTS);
        if (year < 2000 || year > 2100) {
            throw new BusinessException("Năm không hợp lệ");
        }

        Map<String, RevenuePointDTO> byPeriod = new HashMap<>();
        try {
            for (RevenuePointDTO p : reportDAO.revenueByMonth(year)) {
                byPeriod.put(p.getPeriod(), p);
            }
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải báo cáo doanh thu do lỗi cơ sở dữ liệu");
        }

        List<RevenuePointDTO> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            String key = YearMonth.of(year, month).toString();
            result.add(byPeriod.getOrDefault(key, RevenuePointDTO.empty(key)));
        }
        return result;
    }

    /** Sản phẩm bán chạy nhất trong khoảng ngày; limit từ 1 đến MAX_TOP_LIMIT. */
    public List<TopProductDTO> getTopProducts(LocalDate from, LocalDate to, int limit) {
        authBUS.requirePermission(PermissionNames.VIEW_REPORTS);
        requireValidRange(from, to);
        if (limit < 1 || limit > MAX_TOP_LIMIT) {
            throw new BusinessException("Số lượng sản phẩm hiển thị phải từ 1 đến " + MAX_TOP_LIMIT);
        }
        try {
            return reportDAO.topProducts(from, to, limit);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải báo cáo sản phẩm bán chạy do lỗi cơ sở dữ liệu");
        }
    }

    /** Các phiên bản còn từ threshold trở xuống (chưa ngừng kinh doanh), ít hàng nhất trước. */
    public List<ProductListItemDTO> getLowStock(int threshold) {
        if (!authBUS.hasPermission(PermissionNames.VIEW_REPORTS)
                && !authBUS.hasPermission(PermissionNames.MANAGE_INVENTORY)) {
            authBUS.requirePermission(PermissionNames.VIEW_REPORTS);   // ném thông báo chưa đăng nhập / không có quyền
        }
        if (threshold < 0) {
            throw new BusinessException("Ngưỡng tồn kho không được âm");
        }
        ProductFilter filter = new ProductFilter();
        filter.setMaxStock(threshold);
        try {
            List<ProductListItemDTO> rows = new ArrayList<>(productDAO.search(filter));
            rows.removeIf(r -> ProductStatuses.DISCONTINUED.equals(r.getStatus()));
            rows.sort(Comparator.comparingInt(ProductListItemDTO::getStockQuantity)
                    .thenComparing(ProductListItemDTO::getProductName, Comparator.nullsLast(Comparator.naturalOrder())));
            return rows;
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải danh sách hàng sắp hết do lỗi cơ sở dữ liệu");
        }
    }

    /** Các con số tổng quan cho Dashboard, tính tại ngày hôm nay. */
    public DashboardSummaryDTO getDashboardSummary() {
        authBUS.requirePermission(PermissionNames.VIEW_REPORTS);
        try {
            return reportDAO.dashboard(LocalDate.now(), LOW_STOCK_THRESHOLD);
        } catch (SQLException e) {
            throw SqlErrorMapper.toBusinessException(e, "Không thể tải số liệu tổng quan do lỗi cơ sở dữ liệu");
        }
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("Vui lòng chọn khoảng ngày");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("Ngày bắt đầu không được sau ngày kết thúc");
        }
    }
}
