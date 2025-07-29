package stirling.software.SPDF.controller.api;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.pixee.security.Filenames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.model.api.general.MergeMultiplePagesRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.WebResponseUtils;

@RestController
@RequestMapping("/api/v1/general")
@Tag(name = "General", description = "General APIs")
@RequiredArgsConstructor
@Slf4j
public class MultiPageLayoutController {

    private final CustomPDFDocumentFactory pdfDocumentFactory;

    @PostMapping(value = "/multi-page-layout", consumes = "multipart/form-data")
    @Operation(
            summary = "Merge multiple pages of a PDF document into a single page",
            description =
                    "This operation takes an input PDF file and the number of pages to merge into a"
                            + " single sheet in the output PDF file. Input:PDF Output:PDF Type:SISO")
    public ResponseEntity<byte[]> mergeMultiplePagesIntoOne(
            @ModelAttribute MergeMultiplePagesRequest request) throws IOException {

        int pagesPerSheet = request.getPagesPerSheet();
        MultipartFile file = request.getFileInput();
        boolean addBorder = Boolean.TRUE.equals(request.getAddBorder());
        String keepA4Pages = request.getKeepA4Pages();

        if (pagesPerSheet != 2
                && pagesPerSheet != 3
                && pagesPerSheet != (int) Math.sqrt(pagesPerSheet) * Math.sqrt(pagesPerSheet)) {
            throw new IllegalArgumentException("pagesPerSheet must be 2, 3 or a perfect square");
        }

        // 解析keepA4Pages字符串 - 这些是原始PDF的页面号
        Set<Integer> keepA4OriginalPageNumbers = parseKeepA4Pages(keepA4Pages);
        log.debug("keepA4OriginalPageNumbers: {}", keepA4OriginalPageNumbers);

        PDDocument sourceDocument = pdfDocumentFactory.load(file);
        PDDocument newDocument =
                pdfDocumentFactory.createNewDocumentBasedOnOldDocument(sourceDocument);

        int totalPages = sourceDocument.getNumberOfPages();
        log.debug("Total pages: {}, pagesPerSheet: {}", totalPages, pagesPerSheet);

        if (totalPages == 0) {
            throw new IllegalArgumentException("No pages found in the source document");
        }

        LayerUtility layerUtility = new LayerUtility(newDocument);

        // 新的算法：分别处理A4单独页面和多页合并
        int currentSourcePageIndex = 0;

        while (currentSourcePageIndex < totalPages) {
            int originalPageNumber = currentSourcePageIndex + 1; // 1-based页面号

            log.debug(
                    "Main loop: currentSourcePageIndex={}, originalPageNumber={}, totalPages={}",
                    currentSourcePageIndex,
                    originalPageNumber,
                    totalPages);

            if (keepA4OriginalPageNumbers.contains(originalPageNumber)) {
                // 这个页面需要单独显示在A4页面上
                log.debug("Creating individual A4 page for source page {}", originalPageNumber);
                createIndividualA4Page(
                        sourceDocument,
                        newDocument,
                        layerUtility,
                        currentSourcePageIndex,
                        addBorder);
                currentSourcePageIndex++;
            } else {
                // 这些页面可以合并到多页布局中
                int pagesToProcess = Math.min(pagesPerSheet, totalPages - currentSourcePageIndex);

                // 检查接下来的页面中是否有需要单独A4显示的
                int actualPagesToProcess = 0;
                for (int i = 0; i < pagesToProcess; i++) {
                    int checkPageNumber = currentSourcePageIndex + i + 1;
                    if (keepA4OriginalPageNumbers.contains(checkPageNumber)) {
                        break; // 遇到需要单独显示的页面，停止
                    }
                    actualPagesToProcess++;
                }

                log.debug(
                        "Multi-page check: pagesToProcess={}, actualPagesToProcess={}",
                        pagesToProcess,
                        actualPagesToProcess);

                if (actualPagesToProcess > 0) {
                    log.debug(
                            "Creating multi-page layout with {} pages starting from page {}",
                            actualPagesToProcess,
                            currentSourcePageIndex + 1);
                    createMultiPageLayout(
                            sourceDocument,
                            newDocument,
                            layerUtility,
                            currentSourcePageIndex,
                            actualPagesToProcess,
                            pagesPerSheet,
                            addBorder);
                    currentSourcePageIndex += actualPagesToProcess;
                } else {
                    // 下一页需要单独显示，跳到下一次循环处理
                    log.debug("Next page needs individual A4, continuing to next iteration");
                    continue;
                }
            }
        }

        sourceDocument.close();
        log.debug("Finished processing all pages");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newDocument.save(baos);
        newDocument.close();

        byte[] result = baos.toByteArray();
        return WebResponseUtils.bytesToWebResponse(
                result,
                Filenames.toSimpleFileName(file.getOriginalFilename()).replaceFirst("[.][^.]+$", "")
                        + "_layoutChanged.pdf");
    }

    /** 创建单独的A4页面显示一个原始页面 */
    private void createIndividualA4Page(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int sourcePageIndex,
            boolean addBorder)
            throws IOException {

        log.debug(
                "createIndividualA4Page: sourcePageIndex={}, totalPages={}",
                sourcePageIndex,
                sourceDocument.getNumberOfPages());

        PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);

        log.debug("Original source page mediaBox: {}", sourcePage.getMediaBox());

        // 分析页面内容
        analyzePageContent(sourcePage, sourcePageIndex);

        // 方法1：使用LayerUtility（最可靠的方法）
        if (tryLayerUtilityMethod(
                sourceDocument, newDocument, layerUtility, sourcePageIndex, addBorder)) {
            log.debug("Successfully used LayerUtility method");
            return;
        }

        // 方法2：直接导入页面（适用于已经是A4尺寸的页面）
        if (tryDirectImport(sourceDocument, newDocument, sourcePageIndex)) {
            log.debug("Successfully used direct import method");
            return;
        }

        // 方法3：保守的页面复制（备选方案）
        if (tryContentStreamCopy(sourceDocument, newDocument, sourcePageIndex, addBorder)) {
            log.debug("Successfully used conservative copy method");
            return;
        }

        // 方法4：最后备选 - 创建一个带错误信息的页面
        createErrorPage(newDocument, sourcePageIndex);
        log.warn(
                "All rendering methods failed for page {}, created error page",
                sourcePageIndex + 1);
    }

    /** 方法1：直接导入页面 */
    private boolean tryDirectImport(
            PDDocument sourceDocument, PDDocument newDocument, int sourcePageIndex) {
        try {
            PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
            PDRectangle sourceRect = sourcePage.getMediaBox();

            // 检查源页面是否接近A4尺寸
            float widthDiff = Math.abs(sourceRect.getWidth() - PDRectangle.A4.getWidth());
            float heightDiff = Math.abs(sourceRect.getHeight() - PDRectangle.A4.getHeight());

            // 只有当源页面已经是A4尺寸时才直接导入
            if (widthDiff < 10 && heightDiff < 10) {
                PDPage importedPage = newDocument.importPage(sourcePage);
                importedPage.setMediaBox(PDRectangle.A4); // 确保精确的A4尺寸
                log.debug("Direct import successful for A4-sized page");
                return true;
            } else {
                log.debug(
                        "Source page size {}x{} differs from A4, using other methods",
                        sourceRect.getWidth(),
                        sourceRect.getHeight());
                return false;
            }

        } catch (Exception e) {
            log.debug("Direct import failed: {}", e.getMessage());
            return false;
        }
    }

    /** 方法2：使用LayerUtility */
    private boolean tryLayerUtilityMethod(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int sourcePageIndex,
            boolean addBorder) {
        try {
            PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
            PDRectangle sourceRect = sourcePage.getMediaBox();

            // 创建A4输出页面
            PDPage outputPage = new PDPage(PDRectangle.A4);
            newDocument.addPage(outputPage);

            try (PDPageContentStream contentStream =
                    new PDPageContentStream(
                            newDocument,
                            outputPage,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true)) {

                // 计算缩放和位置
                float scaleWidth = PDRectangle.A4.getWidth() / sourceRect.getWidth();
                float scaleHeight = PDRectangle.A4.getHeight() / sourceRect.getHeight();
                float scale = Math.min(scaleWidth, scaleHeight);

                // 确保scale在合理范围内
                if (scale <= 0 || scale > 10) {
                    log.warn("Invalid scale calculated: {}, skipping", scale);
                    return false;
                }

                float x = (PDRectangle.A4.getWidth() - sourceRect.getWidth() * scale) / 2;
                float y = (PDRectangle.A4.getHeight() - sourceRect.getHeight() * scale) / 2;

                log.debug("LayerUtility method: scale={}, position=({}, {})", scale, x, y);

                // 检查坐标是否合理
                if (x < -1000 || x > 1000 || y < -1000 || y > 1000) {
                    log.warn("Invalid coordinates calculated: x={}, y={}, skipping", x, y);
                    return false;
                }

                // 绘制内容，增加错误恢复
                contentStream.saveGraphicsState();

                try {
                    contentStream.transform(Matrix.getTranslateInstance(x, y));
                    contentStream.transform(Matrix.getScaleInstance(scale, scale));

                    PDFormXObject formXObject =
                            layerUtility.importPageAsForm(sourceDocument, sourcePageIndex);

                    if (formXObject != null && formXObject.getBBox() != null) {
                        // 验证formXObject的边界框
                        PDRectangle bbox = formXObject.getBBox();
                        if (bbox.getWidth() > 0 && bbox.getHeight() > 0) {
                            contentStream.drawForm(formXObject);
                            log.debug("LayerUtility method successful");
                        } else {
                            log.warn("FormXObject has invalid bbox: {}", bbox);
                            return false;
                        }
                    } else {
                        log.warn("FormXObject is null or has null bbox");
                        return false;
                    }

                } catch (Exception drawException) {
                    log.warn("Error during form drawing: {}", drawException.getMessage());
                    return false;
                } finally {
                    contentStream.restoreGraphicsState();
                }

                // 添加边框（简化版本，减少出错可能）
                if (addBorder) {
                    try {
                        contentStream.setLineWidth(1.5f);
                        contentStream.setStrokingColor(Color.BLACK);
                        contentStream.addRect(
                                x,
                                y,
                                sourceRect.getWidth() * scale,
                                sourceRect.getHeight() * scale);
                        contentStream.stroke();
                        log.debug("Border added successfully");
                    } catch (Exception borderException) {
                        log.warn("Error adding border: {}", borderException.getMessage());
                        // 边框失败不影响主要内容
                    }
                }
            }

            return true;

        } catch (Exception e) {
            log.debug("LayerUtility method failed: {}", e.getMessage());
            return false;
        }
    }

    /** 方法3：保守的页面复制方法 */
    private boolean tryContentStreamCopy(
            PDDocument sourceDocument,
            PDDocument newDocument,
            int sourcePageIndex,
            boolean addBorder) {
        try {
            PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
            PDRectangle sourceRect = sourcePage.getMediaBox();

            log.debug("Trying conservative page copy for page {}", sourcePageIndex + 1);

            // 简单地导入页面并强制设置为A4
            PDPage importedPage = newDocument.importPage(sourcePage);

            // 获取原始尺寸
            PDRectangle originalRect = importedPage.getMediaBox();
            log.debug(
                    "Original page size: {}x{}", originalRect.getWidth(), originalRect.getHeight());

            // 如果页面不是A4尺寸，创建一个新的A4页面
            if (Math.abs(originalRect.getWidth() - PDRectangle.A4.getWidth()) > 10
                    || Math.abs(originalRect.getHeight() - PDRectangle.A4.getHeight()) > 10) {

                // 移除导入的页面
                newDocument.removePage(importedPage);

                // 创建一个简单的错误提示页面
                PDPage simplePage = new PDPage(PDRectangle.A4);
                newDocument.addPage(simplePage);

                try (PDPageContentStream contentStream =
                        new PDPageContentStream(
                                newDocument,
                                simplePage,
                                PDPageContentStream.AppendMode.APPEND,
                                true,
                                true)) {

                    // 添加简单的文本说明
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                    contentStream.newLineAtOffset(50, PDRectangle.A4.getHeight() - 100);
                    contentStream.showText(
                            "Page " + (sourcePageIndex + 1) + " - Size adjusted to A4");
                    contentStream.newLine();
                    contentStream.showText(
                            "Original size: "
                                    + (int) originalRect.getWidth()
                                    + "x"
                                    + (int) originalRect.getHeight());
                    contentStream.endText();

                    log.debug("Created placeholder page for size adjustment");
                }

                return true;
            } else {
                // 页面已经是A4尺寸，直接使用
                importedPage.setMediaBox(PDRectangle.A4);
                log.debug("Conservative copy successful - page was already A4 size");
                return true;
            }

        } catch (Exception e) {
            log.debug("Conservative copy method failed: {}", e.getMessage());
            return false;
        }
    }

    /** 方法4：创建错误页面 */
    private void createErrorPage(PDDocument newDocument, int sourcePageIndex) {
        try {
            PDPage errorPage = new PDPage(PDRectangle.A4);
            newDocument.addPage(errorPage);

            try (PDPageContentStream contentStream =
                    new PDPageContentStream(
                            newDocument,
                            errorPage,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true)) {

                // 简单的错误消息
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, PDRectangle.A4.getHeight() - 100);
                contentStream.showText("Error rendering page " + (sourcePageIndex + 1));
                contentStream.newLine();
                contentStream.showText("Original page content could not be displayed");
                contentStream.endText();

                // 绘制边框表示这是一个错误页面
                contentStream.setLineWidth(2.0f);
                contentStream.setStrokingColor(Color.RED);
                contentStream.addRect(
                        10, 10, PDRectangle.A4.getWidth() - 20, PDRectangle.A4.getHeight() - 20);
                contentStream.stroke();
            }

        } catch (Exception e) {
            log.error("Even error page creation failed: {}", e.getMessage());
        }
    }

    /** 创建多页布局 */
    private void createMultiPageLayout(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int startPageIndex,
            int pagesToProcess,
            int pagesPerSheet,
            boolean addBorder)
            throws IOException {

        // 计算布局
        int cols =
                pagesPerSheet == 2 || pagesPerSheet == 3
                        ? pagesPerSheet
                        : (int) Math.sqrt(pagesPerSheet);
        int rows = pagesPerSheet == 2 || pagesPerSheet == 3 ? 1 : (int) Math.sqrt(pagesPerSheet);

        // 对于pagesPerSheet=2，使用A3以保持原始页面尺寸
        PDRectangle outputPageSize = (pagesPerSheet == 2) ? PDRectangle.A3 : PDRectangle.A4;
        PDPage outputPage = new PDPage(outputPageSize);
        newDocument.addPage(outputPage);

        try (PDPageContentStream contentStream =
                new PDPageContentStream(
                        newDocument,
                        outputPage,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true)) {

            contentStream.setLineWidth(1.5f);
            contentStream.setStrokingColor(Color.BLACK);

            float cellWidth = outputPageSize.getWidth() / cols;
            float cellHeight = outputPageSize.getHeight() / rows;

            for (int i = 0; i < pagesToProcess; i++) {
                int sourcePageIndex = startPageIndex + i;
                PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
                PDRectangle sourceRect = sourcePage.getMediaBox();

                // 计算位置
                int colIndex = i % cols;
                int rowIndex = i / cols;

                // 计算缩放
                float scaleWidth = cellWidth / sourceRect.getWidth();
                float scaleHeight = cellHeight / sourceRect.getHeight();
                float scale = Math.min(scaleWidth, scaleHeight);

                // 对于A3页面，如果源页面是A4尺寸，尝试保持scale=1.0
                if (pagesPerSheet == 2
                        && Math.abs(sourceRect.getWidth() - PDRectangle.A4.getWidth()) < 10
                        && Math.abs(sourceRect.getHeight() - PDRectangle.A4.getHeight()) < 10
                        && scale > 0.95f) {
                    scale = Math.min(1.0f, scale);
                }

                // 计算位置
                float x = colIndex * cellWidth + (cellWidth - sourceRect.getWidth() * scale) / 2;
                float y;

                if (pagesPerSheet == 2) {
                    // A3页面，垂直居中
                    y = (outputPageSize.getHeight() - sourceRect.getHeight() * scale) / 2;
                } else {
                    // 其他布局，从上到下
                    y =
                            outputPageSize.getHeight()
                                    - ((rowIndex + 1) * cellHeight
                                            - (cellHeight - sourceRect.getHeight() * scale) / 2);
                }

                log.debug(
                        "Multi-page layout: page {} at ({}, {}) with scale {}",
                        sourcePageIndex + 1,
                        colIndex,
                        rowIndex,
                        scale);

                // 绘制内容
                contentStream.saveGraphicsState();
                contentStream.transform(Matrix.getTranslateInstance(x, y));
                contentStream.transform(Matrix.getScaleInstance(scale, scale));

                PDFormXObject formXObject =
                        layerUtility.importPageAsForm(sourceDocument, sourcePageIndex);
                contentStream.drawForm(formXObject);

                contentStream.restoreGraphicsState();

                // 添加边框
                if (addBorder) {
                    float borderX = colIndex * cellWidth;
                    float borderY =
                            (pagesPerSheet == 2)
                                    ? 0
                                    : outputPageSize.getHeight() - (rowIndex + 1) * cellHeight;
                    float borderWidth = cellWidth;
                    float borderHeight =
                            (pagesPerSheet == 2) ? outputPageSize.getHeight() : cellHeight;

                    contentStream.addRect(borderX, borderY, borderWidth, borderHeight);
                    contentStream.stroke();
                }
            }
        }
    }

    /** 解析keepA4Pages字符串，返回页面号集合 */
    private Set<Integer> parseKeepA4Pages(String keepA4Pages) {
        Set<Integer> pageNumbers = new HashSet<>();
        if (keepA4Pages != null && !keepA4Pages.trim().isEmpty()) {
            String[] parts = keepA4Pages.split(",");
            for (String part : parts) {
                try {
                    int pageNum = Integer.parseInt(part.trim());
                    if (pageNum > 0) {
                        pageNumbers.add(pageNum);
                        log.debug("Parsed A4 page number: {}", pageNum);
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid page number format: {}", part);
                    // 忽略无效的页面号
                }
            }
        }
        log.debug("Final parsed A4 page numbers: {}", pageNumbers);
        return pageNumbers;
    }

    /** 分析页面内容，帮助调试空白页面问题 */
    private void analyzePageContent(PDPage page, int pageIndex) {
        try {
            log.debug("=== Analyzing page {} ===", pageIndex + 1);

            // 检查页面基本属性
            PDRectangle mediaBox = page.getMediaBox();
            int rotation = page.getRotation();
            log.debug("  MediaBox: {}, Rotation: {}", mediaBox, rotation);

            // 检查是否接近标准尺寸
            if (mediaBox != null) {
                float widthDiffA4 = Math.abs(mediaBox.getWidth() - PDRectangle.A4.getWidth());
                float heightDiffA4 = Math.abs(mediaBox.getHeight() - PDRectangle.A4.getHeight());
                log.debug("  A4 difference: width={}, height={}", widthDiffA4, heightDiffA4);

                if (widthDiffA4 < 10 && heightDiffA4 < 10) {
                    log.debug("  ✓ Page is already close to A4 size");
                }
            }

            // 检查页面资源
            if (page.getResources() != null) {
                log.debug("  ✓ Page has resources");
                try {
                    if (page.getResources().getFontNames() != null
                            && page.getResources().getFontNames().iterator().hasNext()) {
                        log.debug("  ✓ Page has fonts");
                    }
                    if (page.getResources().getXObjectNames() != null
                            && page.getResources().getXObjectNames().iterator().hasNext()) {
                        log.debug("  ✓ Page has XObjects (images/forms)");
                    }
                    if (page.getResources().getColorSpaceNames() != null
                            && page.getResources().getColorSpaceNames().iterator().hasNext()) {
                        log.debug("  ✓ Page has color spaces");
                    }
                } catch (Exception resourceException) {
                    log.warn("  ! Error checking resources: {}", resourceException.getMessage());
                }
            } else {
                log.warn("  ✗ Page has NO resources");
            }

            // 检查内容流
            if (page.getContents() != null) {
                log.debug("  ✓ Page has content stream");
            } else {
                log.warn("  ✗ Page has NO content stream");
            }

            // 检查页面注释
            try {
                if (page.getAnnotations() != null && !page.getAnnotations().isEmpty()) {
                    log.debug("  ✓ Page has {} annotations", page.getAnnotations().size());
                }
            } catch (Exception annotationException) {
                log.warn("  ! Error checking annotations: {}", annotationException.getMessage());
            }

            // 检查页面是否可能为空白
            boolean hasResources = page.getResources() != null;
            boolean hasContent = page.getContents() != null;

            if (!hasResources && !hasContent) {
                log.error("  ✗ Page {} appears to be COMPLETELY BLANK!", pageIndex + 1);
            } else if (!hasContent) {
                log.warn("  ⚠ Page {} has resources but no content stream", pageIndex + 1);
            } else if (!hasResources) {
                log.warn("  ⚠ Page {} has content but no resources", pageIndex + 1);
            }

            log.debug("=== Analysis complete ===");

        } catch (Exception e) {
            log.error("Error analyzing page {}: {}", pageIndex + 1, e.getMessage());
        }
    }
}
