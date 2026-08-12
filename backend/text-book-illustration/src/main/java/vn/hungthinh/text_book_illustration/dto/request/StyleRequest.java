package vn.hungthinh.text_book_illustration.dto.request;

public record StyleRequest(String style) {

    public boolean hasUserStyle() {
        return style != null && !style.isBlank();
    }
}
