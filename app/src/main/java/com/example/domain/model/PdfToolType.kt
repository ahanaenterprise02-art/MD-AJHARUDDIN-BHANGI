package com.example.domain.model

enum class PdfToolType(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val subtitleEn: String,
    val subtitleBn: String
) {
    PDF_TO_JPG("pdf_to_jpg", "PDF to JPG", "পিডিএফ থেকে জেপিজি", "Export pages as JPG images", "পৃষ্ঠাগুলো জেপিজি ছবিতে রূপান্তর"),
    PDF_TO_PNG("pdf_to_png", "PDF to PNG", "পিডিএফ থেকে পিএনজি", "Export crisp lossless PNGs", "উচ্চমানের পিএনজি ছবিতে রূপান্তর"),
    MERGE_PDF("merge_pdf", "Merge PDF", "পিডিএফ যুক্ত করুন", "Combine multiple PDFs into one", "একাধিক পিডিএফ একত্রিত করুন"),
    SPLIT_PDF("split_pdf", "Split PDF", "পিডিএফ ভাগ করুন", "Extract pages or split ranges", "পৃষ্ঠা আলাদা বা ভাগ করুন"),
    COMPRESS_PDF("compress_pdf", "Compress PDF", "পিডিএফ কম্প্রেস", "Reduce PDF file size locally", "পিডিএফ ফাইলের আকার কমান"),
    ROTATE_PDF("rotate_pdf", "Rotate PDF", "পিডিএফ ঘোরান", "Rotate all or selected pages", "সব বা নির্দিষ্ট পৃষ্ঠা ঘোরান"),
    DELETE_PAGES("delete_pages", "Delete Pages", "পৃষ্ঠা মুছুন", "Remove unwanted PDF pages", "অপ্রয়োজনীয় পৃষ্ঠা বাদ দিন"),
    ADD_TEXT("add_text", "Add Text", "টেক্সট যোগ করুন", "Write custom text onto pages", "পৃষ্ঠায় কাস্টম লেখা যোগ করুন"),
    ADD_SIGNATURE("add_signature", "Add Signature", "স্বাক্ষর যোগ করুন", "Draw & stamp your signature", "স্বাক্ষর এঁকে পিডিএফে বসান"),
    ADD_WATERMARK("add_watermark", "Add Watermark", "ওয়াটারমার্ক", "Stamp diagonal text watermark", "সব পৃষ্ঠায় জলছাপ যুক্ত করুন"),
    ADD_PAGE_NUMBERS("add_page_numbers", "Page Numbers", "পৃষ্ঠা নম্বর", "Number every PDF page", "প্রতিটি পৃষ্ঠায় নম্বর যুক্ত করুন"),
    LOCK_PDF("lock_pdf", "Lock PDF", "পিডিএফ লক", "Protect PDF with a password", "পাসওয়ার্ড দিয়ে পিডিএফ সুরক্ষিত করুন"),
    UNLOCK_PDF("unlock_pdf", "Unlock PDF", "পিডিএফ আনলক", "Remove password protection", "পাসওয়ার্ড সুরক্ষা সরান")
}
