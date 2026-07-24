package com.bookinghealthy.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bảng dữ liệu bác sĩ bổ sung: 5 bác sĩ cho MỖI chuyên khoa (22 khoa = 110 bác sĩ).
 *
 * Chỉ chứa dữ liệu thuần — phần khởi tạo (User / Doctor / Schedule) nằm ở
 * {@link DataInitializer#ensureExtraDoctors()} để đảm bảo chạy sau khi đã có
 * đủ Role và Department.
 *
 * Các trường suy ra từ họ tên (xem {@code DataInitializer.slugify}):
 *  - username : "bs_" + slug   (vd: Nguyễn Đức Toàn -> bs_nguyenductoan)
 *  - email    : slug + "@meditrust.vn"
 *  - avatar   : "bs-" + slug + ".jpg" — ảnh nằm trong thư mục uploads/ cạnh tiến trình chạy,
 *               được phục vụ qua /uploads/** (xem WebConfig).
 *
 * Tên khoa phải TRÙNG KHỚP tên trong bảng departments do DataInitializer tạo.
 */
public final class DoctorSeedData {

    private DoctorSeedData() {
    }

    /**
     * @param fullName        họ tên đầy đủ (duy nhất — dùng để sinh username/email/avatar)
     * @param gender          "Nam" hoặc "Nữ" (quyết định ảnh đại diện)
     * @param degree          học vị hiển thị trước tên trên giao diện
     * @param experienceYears số năm kinh nghiệm
     * @param price           giá khám (VNĐ)
     * @param expertise       thế mạnh chuyên môn
     * @param training        nơi đào tạo / chứng chỉ
     */
    public record SeedDoctor(String fullName, String gender, String degree, int experienceYears,
                             long price, String expertise, String training) {
    }

    private static SeedDoctor d(String fullName, String gender, String degree, int experienceYears,
                                long price, String expertise, String training) {
        return new SeedDoctor(fullName, gender, degree, experienceYears, price, expertise, training);
    }

    /** Khoa -> 5 bác sĩ. Dùng LinkedHashMap để thứ tự khởi tạo luôn ổn định. */
    public static final Map<String, List<SeedDoctor>> BY_DEPARTMENT = new LinkedHashMap<>();

    static {
        BY_DEPARTMENT.put("Tim mạch", List.of(
                d("Nguyễn Đức Toàn", "Nam", "Tiến sĩ", 20, 600000,
                        "can thiệp động mạch vành qua da, đặt stent trong nhồi máu cơ tim cấp",
                        "Bác sĩ nội trú Tim mạch - Đại học Y Hà Nội, chứng chỉ can thiệp tim mạch"),
                d("Trần Thị Mỹ Duyên", "Nữ", "Bác sĩ CKII", 16, 450000,
                        "siêu âm tim Doppler, quản lý suy tim mạn và tăng huyết áp kháng trị",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Nội tim mạch"),
                d("Lê Hoàng Nam", "Nam", "Thạc sĩ", 12, 400000,
                        "rối loạn nhịp tim, Holter điện tâm đồ 24 giờ, điều trị rung nhĩ",
                        "Thạc sĩ Nội khoa - Đại học Y Dược Huế, chứng chỉ điện sinh lý tim"),
                d("Phạm Quốc Việt", "Nam", "Bác sĩ CKI", 9, 350000,
                        "điện tâm đồ gắng sức, phục hồi chức năng tim mạch sau nhồi máu",
                        "Đại học Y Dược Hải Phòng, chuyên khoa I Nội tim mạch"),
                d("Vũ Thị Ngọc Hà", "Nữ", "Bác sĩ", 7, 300000,
                        "tầm soát rối loạn mỡ máu, tư vấn dự phòng bệnh mạch vành",
                        "Đại học Y Dược Thái Bình, chứng chỉ siêu âm tim cơ bản")
        ));

        BY_DEPARTMENT.put("Nội thần kinh", List.of(
                d("Đặng Minh Khôi", "Nam", "Tiến sĩ", 18, 600000,
                        "điều trị đột quỵ cấp, tiêu sợi huyết đường tĩnh mạch trong giờ vàng",
                        "Tiến sĩ Y học - Đại học Y Hà Nội, chứng chỉ đột quỵ não"),
                d("Nguyễn Thị Hoài Thu", "Nữ", "Bác sĩ CKII", 15, 450000,
                        "động kinh, đọc điện não đồ, điều trị đau đầu Migraine",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Thần kinh"),
                d("Hoàng Văn Trường", "Nam", "Thạc sĩ", 11, 400000,
                        "bệnh Parkinson và các rối loạn vận động, sa sút trí tuệ",
                        "Thạc sĩ Thần kinh - Học viện Quân y"),
                d("Bùi Thị Kim Chi", "Nữ", "Bác sĩ CKI", 8, 350000,
                        "đau thần kinh tọa, thoát vị đĩa đệm, đo điện cơ",
                        "Đại học Y khoa Phạm Ngọc Thạch, chuyên khoa I Thần kinh"),
                d("Lý Gia Bảo", "Nam", "Bác sĩ", 6, 300000,
                        "mất ngủ mạn tính, rối loạn tiền đình, đau đầu căng cơ",
                        "Đại học Y Dược Cần Thơ, chứng chỉ thần kinh lâm sàng")
        ));

        BY_DEPARTMENT.put("Nhi khoa", List.of(
                d("Trịnh Thu Trang", "Nữ", "Bác sĩ CKII", 17, 450000,
                        "hô hấp nhi, hen phế quản và viêm tiểu phế quản ở trẻ nhỏ",
                        "Đại học Y Hà Nội, chuyên khoa II Nhi"),
                d("Nguyễn Anh Kiệt", "Nam", "Thạc sĩ", 13, 400000,
                        "tiêu hóa nhi, tiêu chảy kéo dài, kém hấp thu ở trẻ",
                        "Thạc sĩ Nhi khoa - Đại học Y Dược TP.HCM"),
                d("Phan Thị Bích Ngọc", "Nữ", "Bác sĩ CKI", 10, 350000,
                        "sơ sinh, vàng da sơ sinh, tư vấn nuôi con bằng sữa mẹ",
                        "Đại học Y Dược Huế, chuyên khoa I Nhi - Sơ sinh"),
                d("Đỗ Nhật Minh", "Nam", "Bác sĩ", 7, 300000,
                        "tiêm chủng, theo dõi tăng trưởng và dinh dưỡng trẻ em",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ tiêm chủng an toàn"),
                d("Võ Thị Cẩm Tú", "Nữ", "Bác sĩ", 6, 300000,
                        "dị ứng nhi, chàm sữa, viêm mũi dị ứng ở trẻ",
                        "Đại học Y Dược Cần Thơ, chứng chỉ dị ứng - miễn dịch lâm sàng")
        ));

        BY_DEPARTMENT.put("Da liễu", List.of(
                d("Nguyễn Thị Diễm My", "Nữ", "Bác sĩ CKII", 15, 450000,
                        "trứng cá nội tiết, nám má, laser điều trị rối loạn sắc tố",
                        "Đại học Y Hà Nội, chuyên khoa II Da liễu"),
                d("Trần Hải Đăng", "Nam", "Thạc sĩ", 12, 400000,
                        "vảy nến, viêm da cơ địa, điều trị bằng thuốc sinh học",
                        "Thạc sĩ Da liễu - Đại học Y Dược TP.HCM"),
                d("Lê Thị Phương Anh", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "bệnh da nhiễm trùng, nấm da, ghẻ và bệnh da lây truyền",
                        "Đại học Y Dược Huế, chuyên khoa I Da liễu"),
                d("Huỳnh Tấn Phát", "Nam", "Bác sĩ", 7, 300000,
                        "rụng tóc, bệnh lý móng, tiểu phẫu u lành trên da",
                        "Đại học Y khoa Phạm Ngọc Thạch, chứng chỉ phẫu thuật da"),
                d("Mai Thị Thanh Vân", "Nữ", "Bác sĩ", 5, 280000,
                        "chăm sóc da thẩm mỹ, peel da, trẻ hóa da không xâm lấn",
                        "Đại học Y Dược Cần Thơ, chứng chỉ laser thẩm mỹ da")
        ));

        BY_DEPARTMENT.put("Chấn thương chỉnh hình", List.of(
                d("Nguyễn Bá Hùng", "Nam", "Tiến sĩ", 21, 600000,
                        "thay khớp háng và khớp gối nhân tạo",
                        "Tiến sĩ Ngoại khoa - Đại học Y Hà Nội, tu nghiệp phẫu thuật khớp"),
                d("Trần Văn Lợi", "Nam", "Bác sĩ CKII", 16, 450000,
                        "nội soi khớp gối, tái tạo dây chằng chéo trước",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Chấn thương chỉnh hình"),
                d("Đinh Thị Hồng Vân", "Nữ", "Thạc sĩ", 11, 400000,
                        "chấn thương thể thao, phục hồi chức năng sau phẫu thuật",
                        "Thạc sĩ Ngoại chấn thương - Học viện Quân y"),
                d("Phạm Đức Long", "Nam", "Bác sĩ CKI", 9, 350000,
                        "kết hợp xương gãy chi trên, vi phẫu bàn tay",
                        "Đại học Y Dược Hải Phòng, chuyên khoa I Ngoại chấn thương"),
                d("Ngô Thanh Bình", "Nam", "Bác sĩ", 6, 300000,
                        "bó bột, xử trí trật khớp và chấn thương phần mềm",
                        "Đại học Y Dược Thái Bình, chứng chỉ chấn thương chỉnh hình")
        ));

        BY_DEPARTMENT.put("Nhãn khoa", List.of(
                d("Lê Thị Quỳnh Như", "Nữ", "Bác sĩ CKII", 14, 450000,
                        "phẫu thuật Phaco điều trị đục thủy tinh thể",
                        "Đại học Y Hà Nội, chuyên khoa II Nhãn khoa"),
                d("Nguyễn Hoàng Long", "Nam", "Thạc sĩ", 12, 400000,
                        "glôcôm, đo thị trường và theo dõi nhãn áp",
                        "Thạc sĩ Nhãn khoa - Đại học Y Dược TP.HCM"),
                d("Trần Thị Mai Hương", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "khúc xạ trẻ em, kiểm soát cận thị tiến triển",
                        "Đại học Y Dược Huế, chuyên khoa I Mắt"),
                d("Phạm Minh Hoàng", "Nam", "Bác sĩ", 7, 300000,
                        "khô mắt, viêm kết mạc, chắp lẹo và tiểu phẫu mi mắt",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ nhãn khoa lâm sàng"),
                d("Cao Thị Yến Nhi", "Nữ", "Bác sĩ", 5, 280000,
                        "đo khúc xạ, tư vấn kính áp tròng và vệ sinh mắt",
                        "Đại học Y Dược Cần Thơ, chứng chỉ khúc xạ nhãn khoa")
        ));

        BY_DEPARTMENT.put("Sản phụ khoa", List.of(
                d("Nguyễn Thị Thanh Hằng", "Nữ", "Tiến sĩ", 19, 600000,
                        "thai kỳ nguy cơ cao, tiền sản giật, mổ lấy thai",
                        "Tiến sĩ Sản phụ khoa - Đại học Y Hà Nội"),
                d("Trương Thị Lệ Quyên", "Nữ", "Bác sĩ CKII", 15, 450000,
                        "hiếm muộn, theo dõi rụng trứng, bơm tinh trùng vào buồng tử cung (IUI)",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Sản phụ khoa"),
                d("Đỗ Thị Hồng Hạnh", "Nữ", "Thạc sĩ", 12, 400000,
                        "soi cổ tử cung, tầm soát ung thư cổ tử cung",
                        "Thạc sĩ Sản phụ khoa - Đại học Y Dược Huế"),
                d("Hoàng Thị Nhung", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "siêu âm thai, sàng lọc dị tật thai nhi quý I",
                        "Đại học Y Dược Hải Phòng, chứng chỉ siêu âm sản khoa"),
                d("Nguyễn Văn Chương", "Nam", "Bác sĩ", 8, 300000,
                        "viêm nhiễm phụ khoa, tư vấn kế hoạch hóa gia đình",
                        "Đại học Y Dược Thái Bình, chứng chỉ sản phụ khoa")
        ));

        BY_DEPARTMENT.put("Tiêu hóa", List.of(
                d("Lê Đình Phúc", "Nam", "Tiến sĩ", 18, 550000,
                        "nội soi dạ dày - đại tràng, cắt polyp qua nội soi",
                        "Tiến sĩ Nội tiêu hóa - Đại học Y Hà Nội"),
                d("Nguyễn Thị Vân Anh", "Nữ", "Bác sĩ CKII", 14, 450000,
                        "viêm gan B, viêm gan C mạn tính và xơ gan",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Nội tiêu hóa - gan mật"),
                d("Trần Quốc Bảo", "Nam", "Thạc sĩ", 11, 400000,
                        "viêm loét dạ dày do H. pylori, trào ngược dạ dày thực quản",
                        "Thạc sĩ Nội khoa - Đại học Y Dược Huế, chứng chỉ nội soi tiêu hóa"),
                d("Phạm Thị Thùy Dung", "Nữ", "Bác sĩ CKI", 8, 350000,
                        "hội chứng ruột kích thích, rối loạn tiêu hóa chức năng",
                        "Đại học Y khoa Phạm Ngọc Thạch, chuyên khoa I Nội tiêu hóa"),
                d("Vũ Hồng Sơn", "Nam", "Bác sĩ", 6, 300000,
                        "bệnh trĩ, táo bón mạn tính, tư vấn chế độ ăn",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ tiêu hóa lâm sàng")
        ));

        BY_DEPARTMENT.put("Tiết niệu", List.of(
                d("Nguyễn Trọng Nghĩa", "Nam", "Bác sĩ CKII", 16, 450000,
                        "tán sỏi ngoài cơ thể, nội soi tán sỏi niệu quản",
                        "Đại học Y Hà Nội, chuyên khoa II Ngoại tiết niệu"),
                d("Trần Anh Tuấn", "Nam", "Thạc sĩ", 12, 400000,
                        "u xơ tuyến tiền liệt, rối loạn tiểu tiện ở nam giới lớn tuổi",
                        "Thạc sĩ Ngoại tiết niệu - Đại học Y Dược TP.HCM"),
                d("Lê Thị Kim Oanh", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "nhiễm khuẩn tiết niệu tái phát ở nữ giới",
                        "Đại học Y Dược Huế, chuyên khoa I Nội thận - tiết niệu"),
                d("Đặng Quang Huy", "Nam", "Bác sĩ", 7, 300000,
                        "nam khoa, rối loạn cương dương, vô sinh nam",
                        "Đại học Y Dược Hải Phòng, chứng chỉ nam học"),
                d("Phùng Văn Thái", "Nam", "Bác sĩ", 6, 300000,
                        "sỏi thận, siêu âm hệ tiết niệu, dự phòng tái phát sỏi",
                        "Đại học Y Dược Thái Bình, chứng chỉ siêu âm tổng quát")
        ));

        BY_DEPARTMENT.put("Nội tiết", List.of(
                d("Nguyễn Thị Bích Thủy", "Nữ", "Tiến sĩ", 19, 550000,
                        "đái tháo đường type 1 và type 2, hiệu chỉnh phác đồ insulin",
                        "Tiến sĩ Nội tiết - Đại học Y Hà Nội"),
                d("Hoàng Minh Đức", "Nam", "Bác sĩ CKII", 15, 450000,
                        "bệnh lý tuyến giáp, Basedow, theo dõi nhân giáp",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Nội tiết"),
                d("Trần Thị Lệ Thu", "Nữ", "Thạc sĩ", 11, 400000,
                        "béo phì, rối loạn chuyển hóa, hội chứng buồng trứng đa nang",
                        "Thạc sĩ Nội tiết - Đại học Y Dược Huế"),
                d("Nguyễn Văn Điệp", "Nam", "Bác sĩ CKI", 9, 350000,
                        "biến chứng bàn chân đái tháo đường, chăm sóc vết loét",
                        "Đại học Y Dược Cần Thơ, chuyên khoa I Nội tiết"),
                d("Lâm Thị Thu Hà", "Nữ", "Bác sĩ", 6, 300000,
                        "loãng xương, rối loạn nội tiết tuổi mãn kinh",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ nội tiết lâm sàng")
        ));

        BY_DEPARTMENT.put("Ung bướu", List.of(
                d("Phạm Ngọc Sơn", "Nam", "Tiến sĩ", 22, 600000,
                        "hóa trị ung thư phổi và ung thư đại trực tràng",
                        "Tiến sĩ Ung thư học - Đại học Y Hà Nội"),
                d("Nguyễn Thị Hồng Loan", "Nữ", "Bác sĩ CKII", 17, 500000,
                        "ung thư vú, điều trị nội tiết và hóa trị bổ trợ",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Ung bướu"),
                d("Trần Đăng Khoa", "Nam", "Thạc sĩ", 13, 400000,
                        "u lành và ung thư tuyến giáp, sinh thiết kim dưới siêu âm",
                        "Thạc sĩ Ung bướu - Đại học Y Dược Huế"),
                d("Lê Thị Xuân Mai", "Nữ", "Bác sĩ CKI", 10, 350000,
                        "chăm sóc giảm nhẹ, kiểm soát đau cho người bệnh ung thư",
                        "Đại học Y khoa Phạm Ngọc Thạch, chứng chỉ chăm sóc giảm nhẹ"),
                d("Đỗ Trung Kiên", "Nam", "Bác sĩ", 7, 300000,
                        "tầm soát ung thư sớm, tư vấn yếu tố nguy cơ di truyền",
                        "Đại học Y Dược Cần Thơ, chứng chỉ ung thư lâm sàng")
        ));

        BY_DEPARTMENT.put("Tâm thần", List.of(
                d("Nguyễn Thị Thanh Loan", "Nữ", "Bác sĩ CKII", 16, 450000,
                        "trầm cảm, rối loạn lo âu lan tỏa, trị liệu nhận thức hành vi",
                        "Đại học Y Hà Nội, chuyên khoa II Tâm thần"),
                d("Trần Minh Nhật", "Nam", "Thạc sĩ", 12, 400000,
                        "rối loạn lưỡng cực, tâm thần phân liệt và điều trị duy trì",
                        "Thạc sĩ Tâm thần - Đại học Y Dược TP.HCM"),
                d("Vũ Thị Hải Yến", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "rối loạn giấc ngủ, stress nghề nghiệp, rối loạn dạng cơ thể",
                        "Đại học Y Dược Huế, chuyên khoa I Tâm thần"),
                d("Nguyễn Hữu Thắng", "Nam", "Bác sĩ", 7, 300000,
                        "điều trị nghiện chất, hỗ trợ cai rượu và cai thuốc lá",
                        "Đại học Y Dược Thái Bình, chứng chỉ tâm thần học"),
                d("Đào Thị Ngọc Ánh", "Nữ", "Bác sĩ", 6, 300000,
                        "tâm lý trẻ vị thành niên, rối loạn tăng động giảm chú ý",
                        "Đại học Y khoa Phạm Ngọc Thạch, chứng chỉ tâm thần nhi")
        ));

        BY_DEPARTMENT.put("Tai Mũi Họng", List.of(
                d("Nguyễn Văn Toàn", "Nam", "Bác sĩ CKII", 15, 450000,
                        "phẫu thuật nội soi mũi xoang, chỉnh hình vách ngăn",
                        "Đại học Y Hà Nội, chuyên khoa II Tai Mũi Họng"),
                d("Lê Thị Hồng Phúc", "Nữ", "Thạc sĩ", 12, 400000,
                        "viêm tai giữa, đo thính lực, tư vấn máy trợ thính",
                        "Thạc sĩ Tai Mũi Họng - Đại học Y Dược TP.HCM"),
                d("Trần Công Minh", "Nam", "Bác sĩ CKI", 9, 350000,
                        "cắt amidan, nạo VA cho trẻ em bằng dao plasma",
                        "Đại học Y Dược Huế, chuyên khoa I Tai Mũi Họng"),
                d("Phạm Thị Ánh Tuyết", "Nữ", "Bác sĩ", 7, 300000,
                        "viêm mũi dị ứng, khàn tiếng, polyp dây thanh",
                        "Đại học Y Dược Hải Phòng, chứng chỉ nội soi Tai Mũi Họng"),
                d("Hồ Minh Trí", "Nam", "Bác sĩ", 5, 280000,
                        "ngủ ngáy, hội chứng ngưng thở khi ngủ do tắc nghẽn",
                        "Đại học Y Dược Cần Thơ, chứng chỉ đa ký giấc ngủ")
        ));

        BY_DEPARTMENT.put("Răng hàm mặt", List.of(
                d("Nguyễn Thị Mai Chi", "Nữ", "Bác sĩ CKII", 15, 450000,
                        "cấy ghép Implant, phục hình răng sứ thẩm mỹ",
                        "Đại học Y Hà Nội, chuyên khoa II Răng Hàm Mặt"),
                d("Trần Gia Huy", "Nam", "Thạc sĩ", 12, 400000,
                        "chỉnh nha mắc cài và khay trong suốt",
                        "Thạc sĩ Răng Hàm Mặt - Đại học Y Dược TP.HCM"),
                d("Lê Thanh Tùng", "Nam", "Bác sĩ CKI", 9, 350000,
                        "nhổ răng khôn mọc lệch, tiểu phẫu trong miệng",
                        "Đại học Y Dược Huế, chuyên khoa I Răng Hàm Mặt"),
                d("Đinh Thị Kiều Trang", "Nữ", "Bác sĩ", 7, 300000,
                        "điều trị tủy, trám răng thẩm mỹ, tẩy trắng răng",
                        "Đại học Y Dược Cần Thơ, chứng chỉ nội nha"),
                d("Bùi Nhật Quang", "Nam", "Bác sĩ", 5, 280000,
                        "cạo vôi răng, điều trị viêm nha chu, dự phòng sâu răng",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ nha chu")
        ));

        BY_DEPARTMENT.put("Hô hấp", List.of(
                d("Nguyễn Đăng Quang", "Nam", "Tiến sĩ", 18, 550000,
                        "hen phế quản và bệnh phổi tắc nghẽn mạn tính (COPD)",
                        "Tiến sĩ Nội hô hấp - Đại học Y Hà Nội"),
                d("Trần Thị Thu Hiền", "Nữ", "Bác sĩ CKII", 14, 450000,
                        "lao phổi, viêm phổi cộng đồng, giãn phế quản",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Lao và bệnh phổi"),
                d("Lê Văn Cường", "Nam", "Thạc sĩ", 11, 400000,
                        "nội soi phế quản, sinh thiết phổi xuyên thành ngực",
                        "Thạc sĩ Nội hô hấp - Học viện Quân y"),
                d("Nguyễn Thị Ngọc Linh", "Nữ", "Bác sĩ CKI", 8, 350000,
                        "đo chức năng hô hấp, quản lý ho kéo dài",
                        "Đại học Y Dược Huế, chuyên khoa I Nội hô hấp"),
                d("Phan Thanh Tú", "Nam", "Bác sĩ", 6, 300000,
                        "tư vấn cai thuốc lá, phục hồi chức năng hô hấp",
                        "Đại học Y Dược Thái Bình, chứng chỉ hô hấp lâm sàng")
        ));

        BY_DEPARTMENT.put("Cơ xương khớp", List.of(
                d("Nguyễn Thị Kim Dung", "Nữ", "Bác sĩ CKII", 16, 450000,
                        "viêm khớp dạng thấp, lupus ban đỏ hệ thống",
                        "Đại học Y Hà Nội, chuyên khoa II Nội cơ xương khớp"),
                d("Trần Văn Hòa", "Nam", "Thạc sĩ", 12, 400000,
                        "thoái hóa khớp gối, tiêm nội khớp dưới hướng dẫn siêu âm",
                        "Thạc sĩ Nội khoa - Đại học Y Dược TP.HCM"),
                d("Lê Thị Thanh Thảo", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "bệnh gout, loãng xương sau mãn kinh",
                        "Đại học Y Dược Huế, chuyên khoa I Nội cơ xương khớp"),
                d("Nguyễn Xuân Trường", "Nam", "Bác sĩ", 7, 300000,
                        "đau cột sống thắt lưng, đau vai gáy do tư thế",
                        "Đại học Y Dược Hải Phòng, chứng chỉ cơ xương khớp"),
                d("Hà Thị Vân Khánh", "Nữ", "Bác sĩ", 5, 280000,
                        "vật lý trị liệu và phục hồi chức năng cơ xương khớp",
                        "Đại học Kỹ thuật Y tế Hải Dương, chứng chỉ phục hồi chức năng")
        ));

        BY_DEPARTMENT.put("Thận học", List.of(
                d("Nguyễn Hồng Quân", "Nam", "Tiến sĩ", 18, 550000,
                        "lọc máu chu kỳ, quản lý bệnh thận mạn giai đoạn cuối",
                        "Tiến sĩ Nội thận - Đại học Y Hà Nội"),
                d("Trần Thị Thúy Hằng", "Nữ", "Bác sĩ CKII", 14, 450000,
                        "viêm cầu thận, hội chứng thận hư",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Nội thận"),
                d("Lê Minh Chiến", "Nam", "Thạc sĩ", 11, 400000,
                        "sỏi thận tái phát, bệnh thận do đái tháo đường",
                        "Thạc sĩ Nội khoa - Đại học Y Dược Huế"),
                d("Phạm Thị Hải Yến", "Nữ", "Bác sĩ CKI", 8, 350000,
                        "tăng huyết áp do bệnh thận, rối loạn nước - điện giải",
                        "Đại học Y Dược Cần Thơ, chuyên khoa I Nội thận"),
                d("Đỗ Văn Hiếu", "Nam", "Bác sĩ", 6, 300000,
                        "theo dõi chức năng thận, tư vấn chế độ ăn giảm đạm",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ thận học lâm sàng")
        ));

        BY_DEPARTMENT.put("Huyết học", List.of(
                d("Nguyễn Thị Phương Thảo", "Nữ", "Tiến sĩ", 17, 550000,
                        "bạch cầu cấp, u lympho và hóa trị bệnh máu ác tính",
                        "Tiến sĩ Huyết học - Truyền máu, Đại học Y Hà Nội"),
                d("Trần Đức Anh", "Nam", "Bác sĩ CKII", 14, 450000,
                        "rối loạn đông máu, bệnh Hemophilia",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Huyết học"),
                d("Lê Thị Hồng Nhung", "Nữ", "Thạc sĩ", 11, 400000,
                        "thiếu máu thiếu sắt, Thalassemia và tư vấn tiền hôn nhân",
                        "Thạc sĩ Huyết học - Đại học Y Dược Huế"),
                d("Vũ Đình Nam", "Nam", "Bác sĩ CKI", 8, 350000,
                        "truyền máu an toàn, phản ứng sau truyền máu",
                        "Đại học Y Dược Hải Phòng, chuyên khoa I Huyết học - Truyền máu"),
                d("Nguyễn Thị Tuyết Mai", "Nữ", "Bác sĩ", 6, 300000,
                        "đọc huyết đồ, theo dõi giảm tiểu cầu và tăng bạch cầu",
                        "Đại học Y Dược Thái Bình, chứng chỉ xét nghiệm huyết học")
        ));

        BY_DEPARTMENT.put("Chẩn đoán hình ảnh", List.of(
                d("Trần Quang Vinh", "Nam", "Tiến sĩ", 19, 550000,
                        "đọc phim CT và MRI sọ não - thần kinh",
                        "Tiến sĩ Chẩn đoán hình ảnh - Đại học Y Hà Nội"),
                d("Nguyễn Thị Lan Phương", "Nữ", "Bác sĩ CKII", 15, 450000,
                        "siêu âm tổng quát, siêu âm Doppler mạch máu",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Chẩn đoán hình ảnh"),
                d("Lê Hữu Nghĩa", "Nam", "Thạc sĩ", 11, 400000,
                        "X-quang và chụp cắt lớp vi tính lồng ngực",
                        "Thạc sĩ Chẩn đoán hình ảnh - Đại học Y Dược Huế"),
                d("Phạm Thị Ngọc Diệp", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "chụp nhũ ảnh, tầm soát ung thư vú bằng hình ảnh",
                        "Đại học Y khoa Phạm Ngọc Thạch, chuyên khoa I Chẩn đoán hình ảnh"),
                d("Hoàng Anh Tú", "Nam", "Bác sĩ", 7, 300000,
                        "can thiệp dưới hướng dẫn siêu âm, sinh thiết kim",
                        "Đại học Y Dược Cần Thơ, chứng chỉ siêu âm can thiệp")
        ));

        BY_DEPARTMENT.put("Gây mê hồi sức", List.of(
                d("Nguyễn Thanh Sơn", "Nam", "Tiến sĩ", 20, 550000,
                        "gây mê phẫu thuật tim mạch và phẫu thuật lớn",
                        "Tiến sĩ Gây mê hồi sức - Đại học Y Hà Nội"),
                d("Trần Thị Bảo Trâm", "Nữ", "Bác sĩ CKII", 15, 450000,
                        "gây tê vùng, kiểm soát đau sau mổ",
                        "Đại học Y Dược TP.HCM, chuyên khoa II Gây mê hồi sức"),
                d("Lê Quốc Đạt", "Nam", "Thạc sĩ", 12, 400000,
                        "hồi sức ngoại khoa, thở máy và lọc máu liên tục",
                        "Thạc sĩ Hồi sức cấp cứu - Học viện Quân y"),
                d("Nguyễn Thị Thu Trang", "Nữ", "Bác sĩ CKI", 9, 350000,
                        "gây mê sản khoa, gây tê ngoài màng cứng giảm đau đẻ",
                        "Đại học Y Dược Huế, chuyên khoa I Gây mê hồi sức"),
                d("Đỗ Minh Hoàng", "Nam", "Bác sĩ", 7, 300000,
                        "khám tiền mê, đánh giá nguy cơ trước phẫu thuật",
                        "Đại học Y Dược Thái Bình, chứng chỉ gây mê hồi sức")
        ));

        BY_DEPARTMENT.put("Cấp cứu", List.of(
                d("Nguyễn Văn Dũng", "Nam", "Bác sĩ CKII", 16, 450000,
                        "hồi sinh tim phổi nâng cao, xử trí các loại sốc",
                        "Đại học Y Hà Nội, chuyên khoa II Hồi sức cấp cứu"),
                d("Trần Thị Hồng Ngọc", "Nữ", "Thạc sĩ", 12, 400000,
                        "cấp cứu ngộ độc cấp, xử trí phản vệ",
                        "Thạc sĩ Hồi sức cấp cứu - Đại học Y Dược TP.HCM"),
                d("Lê Anh Tuấn", "Nam", "Bác sĩ CKI", 10, 350000,
                        "cấp cứu chấn thương, xử trí ban đầu đa chấn thương",
                        "Đại học Y Dược Huế, chuyên khoa I Cấp cứu ngoại viện"),
                d("Phạm Thị Mỹ Linh", "Nữ", "Bác sĩ", 7, 300000,
                        "cấp cứu nhi, sốt cao co giật và mất nước ở trẻ",
                        "Đại học Y Dược Cần Thơ, chứng chỉ cấp cứu nhi khoa"),
                d("Nguyễn Hoàng Phúc", "Nam", "Bác sĩ", 6, 300000,
                        "vận chuyển cấp cứu, nhận diện và xử trí đột quỵ giờ vàng",
                        "Đại học Y Dược Hải Phòng, chứng chỉ cấp cứu ngoại viện")
        ));

        BY_DEPARTMENT.put("Y học gia đình", List.of(
                d("Nguyễn Thị Hồng Vân", "Nữ", "Bác sĩ CKII", 15, 400000,
                        "quản lý bệnh mạn tính tại cộng đồng: tăng huyết áp, đái tháo đường",
                        "Đại học Y Hà Nội, chuyên khoa II Y học gia đình"),
                d("Trần Văn Bình", "Nam", "Thạc sĩ", 12, 350000,
                        "khám sức khỏe tổng quát, tầm soát yếu tố nguy cơ tim mạch",
                        "Thạc sĩ Y học gia đình - Đại học Y Dược TP.HCM"),
                d("Lê Thị Ánh Nguyệt", "Nữ", "Bác sĩ CKI", 9, 320000,
                        "chăm sóc sức khỏe người cao tuổi có nhiều bệnh lý phối hợp",
                        "Đại học Y Dược Huế, chuyên khoa I Y học gia đình"),
                d("Đỗ Quang Vinh", "Nam", "Bác sĩ", 7, 300000,
                        "tư vấn dinh dưỡng, tiêm chủng cho người lớn",
                        "Đại học Y Dược Thái Nguyên, chứng chỉ y học gia đình"),
                d("Nguyễn Thị Thu Hương", "Nữ", "Bác sĩ", 6, 280000,
                        "sức khỏe phụ nữ, tư vấn tiền hôn nhân và kế hoạch hóa gia đình",
                        "Đại học Y Dược Thái Bình, chứng chỉ chăm sóc sức khỏe ban đầu")
        ));
    }
}
