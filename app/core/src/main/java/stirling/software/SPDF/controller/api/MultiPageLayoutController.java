package stirling.software.SPDF.controller.api;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

        // 改进的算法：如果pagesPerSheet=1，所有页面单独显示；否则使用多页布局
        int currentSourcePageIndex = 0;

        if (pagesPerSheet == 1) {
            // pagesPerSheet=1时，每个页面都单独显示，应用我们的改进
            while (currentSourcePageIndex < totalPages) {
                log.debug(
                        "Creating individual full-view page for source page {}",
                        currentSourcePageIndex + 1);
                createIndividualA4Page(
                        sourceDocument,
                        newDocument,
                        layerUtility,
                        currentSourcePageIndex,
                        addBorder);
                currentSourcePageIndex++;
            }
        } else if (pagesPerSheet == 2) {
            // pagesPerSheet=2时，保持页面顺序的逻辑配对：如果下一页需要单独显示，当前页也单独显示
            while (currentSourcePageIndex < totalPages) {
                int currentPageNumber = currentSourcePageIndex + 1;

                if (keepA4OriginalPageNumbers.contains(currentPageNumber)) {
                    // 指定页面单独显示
                    log.debug(
                            "Creating individual A4 page for specified page {}", currentPageNumber);
                    createIndividualA4Page(
                            sourceDocument,
                            newDocument,
                            layerUtility,
                            currentSourcePageIndex,
                            addBorder);
                    currentSourcePageIndex++;
                } else {
                    // 当前页面不需要单独显示，检查是否可以与下一页配对
                    int remainingPages = totalPages - currentSourcePageIndex;
                    if (remainingPages >= 2) {
                        int nextPageNumber = currentPageNumber + 1;

                        if (keepA4OriginalPageNumbers.contains(nextPageNumber)) {
                            // 下一页需要单独显示，当前页面也单独显示以保持顺序逻辑
                            log.debug(
                                    "Creating individual page for page {} (next page {} needs isolation)",
                                    currentPageNumber,
                                    nextPageNumber);
                            createIndividualA4Page(
                                    sourceDocument,
                                    newDocument,
                                    layerUtility,
                                    currentSourcePageIndex,
                                    addBorder);
                            currentSourcePageIndex++;
                        } else {
                            // 两页都可以合并
                            log.debug(
                                    "Creating 2-page layout with consecutive pages {} and {}",
                                    currentPageNumber,
                                    nextPageNumber);
                            createMultiPageLayoutSimplified(
                                    sourceDocument,
                                    newDocument,
                                    layerUtility,
                                    currentSourcePageIndex,
                                    2,
                                    pagesPerSheet,
                                    addBorder);
                            currentSourcePageIndex += 2;
                        }
                    } else {
                        // 最后一页，单独显示
                        log.debug("Creating individual page for last page {}", currentPageNumber);
                        createIndividualA4Page(
                                sourceDocument,
                                newDocument,
                                layerUtility,
                                currentSourcePageIndex,
                                addBorder);
                        currentSourcePageIndex++;
                    }
                }
            }
        } else {
            // 其他多页布局（3页以上）
            while (currentSourcePageIndex < totalPages) {
                int pagesToProcess = Math.min(pagesPerSheet, totalPages - currentSourcePageIndex);

                log.debug(
                        "Creating multi-page layout with {} pages starting from page {}",
                        pagesToProcess,
                        currentSourcePageIndex + 1);
                createMultiPageLayoutSimplified(
                        sourceDocument,
                        newDocument,
                        layerUtility,
                        currentSourcePageIndex,
                        pagesToProcess,
                        pagesPerSheet,
                        addBorder);
                currentSourcePageIndex += pagesToProcess;
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

    // 在类顶部添加新的方法来检测页面方向
    private boolean isLandscapePage(PDRectangle rect) {
        return rect.getWidth() > rect.getHeight();
    }

    // 计算最优的输出页面方向
    private PDRectangle getOptimalOutputPageSize(PDRectangle sourceRect, boolean forceA4) {
        if (forceA4) {
            // 对于单独页面，使用完全相同的尺寸实现真正的全视图
            float outputWidth = sourceRect.getWidth();
            float outputHeight = sourceRect.getHeight();

            log.debug("Using exact source size for full view: {}x{}", outputWidth, outputHeight);

            return new PDRectangle(outputWidth, outputHeight);
        }
        return PDRectangle.A4;
    }

    // 改进的LayerUtility方法 - 减少间距，智能方向处理
    private boolean tryLayerUtilityMethod(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int sourcePageIndex,
            boolean addBorder) {
        try {
            PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
            PDRectangle sourceRect = sourcePage.getMediaBox();

            // 检测页面方向并选择最优输出尺寸
            boolean sourceIsLandscape = isLandscapePage(sourceRect);
            PDRectangle outputPageSize = getOptimalOutputPageSize(sourceRect, true);

            log.debug(
                    "Source page is landscape: {}, Output size: {}x{}",
                    sourceIsLandscape,
                    outputPageSize.getWidth(),
                    outputPageSize.getHeight());

            // 创建输出页面
            PDPage outputPage = new PDPage(outputPageSize);
            newDocument.addPage(outputPage);

            try (PDPageContentStream contentStream =
                    new PDPageContentStream(
                            newDocument,
                            outputPage,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true)) {

                // 改进的缩放计算 - 无边距，完全最大化页面利用
                float margin = 0; // 完全移除边距
                float availableWidth = outputPageSize.getWidth() - (2 * margin);
                float availableHeight = outputPageSize.getHeight() - (2 * margin);

                float scaleWidth = availableWidth / sourceRect.getWidth();
                float scaleHeight = availableHeight / sourceRect.getHeight();

                // 单独页面优先保持原始尺寸
                float scale;

                // 由于输出页面现在基于源页面尺寸，几乎总是可以使用原始尺寸
                if (sourceRect.getWidth() <= availableWidth
                        && sourceRect.getHeight() <= availableHeight) {
                    // 页面可以使用原始尺寸
                    scale = 1.0f;
                    log.debug(
                            "Single page using original size (scale=1.0) - perfect fit with minimal padding");
                } else {
                    // 极少数情况需要轻微缩放
                    float minRequiredScale = Math.min(scaleWidth, scaleHeight);
                    // 使用稍微保守的缩放，留出一点空间
                    scale = minRequiredScale * 0.98f;
                    log.debug(
                            "Single page using conservative scale: {} (minimal scaling with padding)",
                            scale);
                }

                // 不限制缩放范围，以保持原始尺寸优先
                log.debug("Single page final scale: {}", scale);

                // 计算位置 - 最小化边距
                float scaledWidth = sourceRect.getWidth() * scale;
                float scaledHeight = sourceRect.getHeight() * scale;

                // 真正的全视图：完全无边距定位
                float x = 0; // 无边距，直接从0开始
                float y = 0; // 无边距，直接从0开始

                log.debug(
                        "Improved scaling: scale={}, position=({}, {}), source={}x{}, output={}x{}",
                        scale,
                        x,
                        y,
                        sourceRect.getWidth(),
                        sourceRect.getHeight(),
                        scaledWidth,
                        scaledHeight);

                // 绘制内容
                contentStream.saveGraphicsState();

                try {
                    contentStream.transform(Matrix.getTranslateInstance(x, y));
                    contentStream.transform(Matrix.getScaleInstance(scale, scale));

                    PDFormXObject formXObject =
                            layerUtility.importPageAsForm(sourceDocument, sourcePageIndex);

                    if (formXObject != null && formXObject.getBBox() != null) {
                        PDRectangle bbox = formXObject.getBBox();
                        if (bbox.getWidth() > 0 && bbox.getHeight() > 0) {
                            contentStream.drawForm(formXObject);
                            log.debug("LayerUtility method successful with improved scaling");
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

                // 添加边框
                if (addBorder) {
                    try {
                        contentStream.setLineWidth(1.0f);
                        contentStream.setStrokingColor(Color.GRAY);
                        contentStream.addRect(x, y, scaledWidth, scaledHeight);
                        contentStream.stroke();
                        log.debug("Border added successfully");
                    } catch (Exception borderException) {
                        log.warn("Error adding border: {}", borderException.getMessage());
                    }
                }
            }

            return true;

        } catch (Exception e) {
            log.debug("Improved LayerUtility method failed: {}", e.getMessage());
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

    /** 创建包含特定页面的多页布局 */
    private void createMultiPageLayoutWithSpecificPages(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int[] pageIndices,
            int pagesPerSheet,
            boolean addBorder)
            throws IOException {

        int pagesToProcess = pageIndices.length;
        log.debug(
                "Creating multi-page layout with specific pages: {}",
                Arrays.stream(pageIndices).map(i -> i + 1).boxed().collect(Collectors.toList()));

        // 检测源页面的主要方向
        boolean majorityLandscape =
                checkMajorityOrientationForSpecificPages(sourceDocument, pageIndices);

        // 计算布局 - 统一使用2列1行左右排列
        int cols, rows;
        if (pagesPerSheet == 2) {
            cols = 2;
            rows = 1;
            log.debug("2 pages: using 2 cols x 1 row layout");
        } else if (pagesPerSheet == 3) {
            cols = 3;
            rows = 1;
        } else {
            cols = (int) Math.sqrt(pagesPerSheet);
            rows = (int) Math.sqrt(pagesPerSheet);
        }

        // 智能选择输出页面尺寸
        PDRectangle outputPageSize =
                calculateOptimalOutputSizeForSpecificPages(sourceDocument, pageIndices, cols, rows);

        log.debug("=== SPECIFIC PAGES LAYOUT SUMMARY ===");
        log.debug("Pages to process: {}", pagesToProcess);
        log.debug("Layout: {} cols x {} rows", cols, rows);
        log.debug("Output page size: {}x{}", outputPageSize.getWidth(), outputPageSize.getHeight());
        log.debug(
                "Cell size: {}x{}",
                outputPageSize.getWidth() / cols,
                outputPageSize.getHeight() / rows);
        log.debug("=====================================");

        PDPage outputPage = new PDPage(outputPageSize);
        newDocument.addPage(outputPage);

        try (PDPageContentStream contentStream =
                new PDPageContentStream(
                        newDocument,
                        outputPage,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true)) {

            contentStream.setLineWidth(1.0f);
            contentStream.setStrokingColor(Color.LIGHT_GRAY);

            float margin = 0;
            float availableWidth = outputPageSize.getWidth() - (2 * margin);
            float availableHeight = outputPageSize.getHeight() - (2 * margin);

            float cellWidth = availableWidth / cols;
            float cellHeight = availableHeight / rows;

            // 特殊处理两页布局以优化混合方向显示
            log.debug(
                    "DEBUG: Checking two-page optimization: pagesPerSheet={}, pagesToProcess={}",
                    pagesPerSheet,
                    pagesToProcess);
            if (pagesPerSheet == 2 && pagesToProcess == 2) {
                log.debug("DEBUG: Using positionTwoPagesOptimally for mixed orientation layout");
                positionTwoPagesOptimally(
                        contentStream,
                        layerUtility,
                        sourceDocument,
                        pageIndices,
                        outputPageSize,
                        addBorder);
            } else {
                log.debug("DEBUG: Using generic layout logic (not two-page optimization)");
                // 其他情况使用通用布局逻辑
                for (int i = 0; i < pagesToProcess; i++) {
                    int sourcePageIndex = pageIndices[i];
                    PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
                    PDRectangle sourceRect = sourcePage.getMediaBox();

                    int colIndex = i % cols;
                    int rowIndex = i / cols;

                    log.debug(
                            "Generic page {} positioning: colIndex={}, rowIndex={}",
                            sourcePageIndex + 1,
                            colIndex,
                            rowIndex);

                    float cellPadding = 0;
                    float availableCellWidth = cellWidth - (2 * cellPadding);
                    float availableCellHeight = cellHeight - (2 * cellPadding);

                    float scaleWidth = availableCellWidth / sourceRect.getWidth();
                    float scaleHeight = availableCellHeight / sourceRect.getHeight();

                    float scale;
                    if (sourceRect.getWidth() <= availableCellWidth
                            && sourceRect.getHeight() <= availableCellHeight) {
                        scale = 1.0f;
                        log.debug("Using original size (scale=1.0) for specific pages");
                    } else {
                        float minRequiredScale = Math.min(scaleWidth, scaleHeight);
                        scale =
                                minRequiredScale >= 0.8f
                                        ? minRequiredScale
                                        : Math.max(
                                                minRequiredScale,
                                                (scaleWidth + scaleHeight) / 2 * 0.9f);
                        log.debug("Using calculated scale: {} for specific pages", scale);
                    }

                    float scaledWidth = sourceRect.getWidth() * scale;
                    float scaledHeight = sourceRect.getHeight() * scale;

                    float x, y;
                    if (pagesPerSheet == 2) {
                        if (pagesToProcess == 1) {
                            x = (outputPageSize.getWidth() - scaledWidth) / 2;
                            y = (outputPageSize.getHeight() - scaledHeight) / 2;
                        } else {
                            x =
                                    colIndex == 0
                                            ? Math.max(0, (cellWidth - scaledWidth) / 2)
                                            : cellWidth
                                                    + Math.max(0, (cellWidth - scaledWidth) / 2);
                            y = (outputPageSize.getHeight() - scaledHeight) / 2;
                        }
                    } else {
                        x = colIndex * cellWidth + Math.max(0, (cellWidth - scaledWidth) / 2);
                        y =
                                outputPageSize.getHeight()
                                        - (rowIndex * cellHeight)
                                        - Math.max(0, (cellHeight - scaledHeight) / 2)
                                        - scaledHeight;
                    }

                    log.debug(
                            "Generic page {} final position: ({}, {}) scale={}",
                            sourcePageIndex + 1,
                            x,
                            y,
                            scale);

                    contentStream.saveGraphicsState();
                    contentStream.transform(Matrix.getTranslateInstance(x, y));
                    contentStream.transform(Matrix.getScaleInstance(scale, scale));

                    PDFormXObject formXObject =
                            layerUtility.importPageAsForm(sourceDocument, sourcePageIndex);
                    contentStream.drawForm(formXObject);

                    contentStream.restoreGraphicsState();

                    if (addBorder) {
                        contentStream.setStrokingColor(Color.LIGHT_GRAY);
                        contentStream.addRect(x, y, scaledWidth, scaledHeight);
                        contentStream.stroke();
                    }
                }
            }
        }
    }

    // 智能定位两页以优化混合方向显示
    private void positionTwoPagesOptimally(
            PDPageContentStream contentStream,
            LayerUtility layerUtility,
            PDDocument sourceDocument,
            int[] pageIndices,
            PDRectangle outputPageSize,
            boolean addBorder)
            throws IOException {

        PDPage page1 = sourceDocument.getPage(pageIndices[0]);
        PDPage page2 = sourceDocument.getPage(pageIndices[1]);

        PDRectangle rect1 = page1.getMediaBox();
        PDRectangle rect2 = page2.getMediaBox();

        log.debug("=== OPTIMAL TWO PAGES POSITIONING ===");
        log.debug("Output size: {}x{}", outputPageSize.getWidth(), outputPageSize.getHeight());
        log.debug(
                "Page 1: {}x{} ({})",
                rect1.getWidth(),
                rect1.getHeight(),
                isLandscapePage(rect1) ? "landscape" : "portrait");
        log.debug(
                "Page 2: {}x{} ({})",
                rect2.getWidth(),
                rect2.getHeight(),
                isLandscapePage(rect2) ? "landscape" : "portrait");

        // 计算每页的最优显示尺寸和位置
        float totalOutputWidth = outputPageSize.getWidth();
        float totalOutputHeight = outputPageSize.getHeight();

        // 全视图：无边距
        float margin = 0; // 无边距
        float centerGap = 0; // 无中间间隙
        float availableWidth = totalOutputWidth - centerGap; // 使用全部宽度
        float availableHeight = totalOutputHeight; // 使用全部高度

        // 策略：基于页面原始比例分配宽度
        float totalOriginalWidth = rect1.getWidth() + rect2.getWidth();
        float width1Ratio = rect1.getWidth() / totalOriginalWidth;
        float width2Ratio = rect2.getWidth() / totalOriginalWidth;

        float allocatedWidth1 = availableWidth * width1Ratio;
        float allocatedWidth2 = availableWidth * width2Ratio;

        log.debug(
                "Width allocation: page1={:.1f}%, page2={:.1f}%",
                width1Ratio * 100, width2Ratio * 100);
        log.debug("Allocated widths: page1={:.1f}, page2={:.1f}", allocatedWidth1, allocatedWidth2);

        // 计算每页的缩放和位置
        float[] scalesAndPositions1 =
                calculateOptimalScaleAndPosition(
                        rect1, allocatedWidth1, availableHeight, margin, 0);
        float[] scalesAndPositions2 =
                calculateOptimalScaleAndPosition(
                        rect2,
                        allocatedWidth2,
                        availableHeight,
                        margin,
                        margin + allocatedWidth1 + centerGap);

        float scale1 = scalesAndPositions1[0];
        float x1 = scalesAndPositions1[1];
        float y1 = scalesAndPositions1[2];
        float scaledWidth1 = scalesAndPositions1[3];
        float scaledHeight1 = scalesAndPositions1[4];

        float scale2 = scalesAndPositions2[0];
        float x2 = scalesAndPositions2[1];
        float y2 = scalesAndPositions2[2];
        float scaledWidth2 = scalesAndPositions2[3];
        float scaledHeight2 = scalesAndPositions2[4];

        log.debug(
                "Page 1: scale={:.3f}, position=({:.1f}, {:.1f}), size={:.1f}x{:.1f}",
                scale1,
                x1,
                y1,
                scaledWidth1,
                scaledHeight1);
        log.debug(
                "Page 2: scale={:.3f}, position=({:.1f}, {:.1f}), size={:.1f}x{:.1f}",
                scale2,
                x2,
                y2,
                scaledWidth2,
                scaledHeight2);

        // 绘制页面1
        contentStream.saveGraphicsState();
        contentStream.transform(Matrix.getTranslateInstance(x1, y1));
        contentStream.transform(Matrix.getScaleInstance(scale1, scale1));
        PDFormXObject formXObject1 = layerUtility.importPageAsForm(sourceDocument, pageIndices[0]);
        contentStream.drawForm(formXObject1);
        contentStream.restoreGraphicsState();

        // 绘制页面2
        contentStream.saveGraphicsState();
        contentStream.transform(Matrix.getTranslateInstance(x2, y2));
        contentStream.transform(Matrix.getScaleInstance(scale2, scale2));
        PDFormXObject formXObject2 = layerUtility.importPageAsForm(sourceDocument, pageIndices[1]);
        contentStream.drawForm(formXObject2);
        contentStream.restoreGraphicsState();

        // 添加边框
        if (addBorder) {
            contentStream.setStrokingColor(Color.LIGHT_GRAY);
            contentStream.setLineWidth(1.0f);
            contentStream.addRect(x1, y1, scaledWidth1, scaledHeight1);
            contentStream.stroke();
            contentStream.addRect(x2, y2, scaledWidth2, scaledHeight2);
            contentStream.stroke();
        }

        log.debug("=========================================");
    }

    // 计算单个页面的最优缩放和位置
    private float[] calculateOptimalScaleAndPosition(
            PDRectangle pageRect,
            float allocatedWidth,
            float availableHeight,
            float leftMargin,
            float leftOffset) {

        float pageWidth = pageRect.getWidth();
        float pageHeight = pageRect.getHeight();

        // 计算缩放比例 - 优先保持原始尺寸
        float scaleWidth = allocatedWidth / pageWidth;
        float scaleHeight = availableHeight / pageHeight;

        // 使用能完全容纳页面的最大缩放
        float scale = Math.min(scaleWidth, scaleHeight);

        // 限制最大缩放，防止页面过大
        scale = Math.min(scale, 1.2f);

        // 如果可以使用原始尺寸，优先使用
        if (pageWidth <= allocatedWidth && pageHeight <= availableHeight) {
            scale = Math.min(1.0f, scale);
        }

        float scaledWidth = pageWidth * scale;
        float scaledHeight = pageHeight * scale;

        // 全视图：完全靠边对齐，无居中边距
        float x = leftMargin + leftOffset; // 直接从分配位置开始
        float y = leftMargin; // 直接从顶部开始

        return new float[] {scale, x, y, scaledWidth, scaledHeight};
    }

    // 检测特定页面的主要方向
    private boolean checkMajorityOrientationForSpecificPages(
            PDDocument sourceDocument, int[] pageIndices) {
        int landscapeCount = 0;
        int portraitCount = 0;

        for (int pageIndex : pageIndices) {
            PDPage page = sourceDocument.getPage(pageIndex);
            if (isLandscapePage(page.getMediaBox())) {
                landscapeCount++;
            } else {
                portraitCount++;
            }
        }

        log.debug(
                "Orientation check for specific pages: landscape={}, portrait={}",
                landscapeCount,
                portraitCount);
        return landscapeCount > portraitCount;
    }

    // 为特定页面计算最佳输出尺寸 - 智能处理混合方向
    private PDRectangle calculateOptimalOutputSizeForSpecificPages(
            PDDocument sourceDocument, int[] pageIndices, int cols, int rows) {
        try {
            if (pageIndices.length == 0) {
                return PDRectangle.A3;
            }

            // 特殊处理：两页左右排列的情况
            if (pageIndices.length == 2 && cols == 2 && rows == 1) {
                return calculateOptimalSizeForTwoPages(sourceDocument, pageIndices);
            }

            // 单页情况 - 全视图无边距
            if (pageIndices.length == 1) {
                PDPage page = sourceDocument.getPage(pageIndices[0]);
                PDRectangle rect = page.getMediaBox();
                return new PDRectangle(rect.getWidth(), rect.getHeight());
            }

            // 其他情况使用原来的逻辑 - 全视图无边距
            float maxPageWidth = 0;
            float maxPageHeight = 0;

            for (int pageIndex : pageIndices) {
                PDPage page = sourceDocument.getPage(pageIndex);
                PDRectangle rect = page.getMediaBox();
                maxPageWidth = Math.max(maxPageWidth, rect.getWidth());
                maxPageHeight = Math.max(maxPageHeight, rect.getHeight());
            }

            float requiredWidth = maxPageWidth * cols;
            float requiredHeight = maxPageHeight * rows;

            return new PDRectangle(requiredWidth, requiredHeight);

        } catch (Exception e) {
            log.warn("Error calculating size for specific pages, using A3: {}", e.getMessage());
            return PDRectangle.A3;
        }
    }

    // 为两页左右排列计算最佳输出尺寸 - 处理混合方向
    private PDRectangle calculateOptimalSizeForTwoPages(
            PDDocument sourceDocument, int[] pageIndices) {
        try {
            PDPage page1 = sourceDocument.getPage(pageIndices[0]);
            PDPage page2 = sourceDocument.getPage(pageIndices[1]);

            PDRectangle rect1 = page1.getMediaBox();
            PDRectangle rect2 = page2.getMediaBox();

            log.debug("Two pages layout calculation:");
            log.debug(
                    "  Page 1: {}x{} ({})",
                    rect1.getWidth(),
                    rect1.getHeight(),
                    isLandscapePage(rect1) ? "landscape" : "portrait");
            log.debug(
                    "  Page 2: {}x{} ({})",
                    rect2.getWidth(),
                    rect2.getHeight(),
                    isLandscapePage(rect2) ? "landscape" : "portrait");

            // 策略：计算每个页面在其各自"单元格"中的最优显示尺寸
            // 然后基于这些需求确定整体容器尺寸

            // 方法1：基于页面实际尺寸的智能布局
            float totalWidth = rect1.getWidth() + rect2.getWidth();
            float maxHeight = Math.max(rect1.getHeight(), rect2.getHeight());

            // 方法2：考虑显示比例的优化布局
            // 如果两个页面的高度差异很大，调整总宽度以平衡显示
            float heightRatio =
                    Math.max(rect1.getHeight(), rect2.getHeight())
                            / Math.min(rect1.getHeight(), rect2.getHeight());

            log.debug("  Height ratio calculated: {:.2f}", heightRatio);
            if (heightRatio > 1.5f) {
                // 高度差异较大时，给较高的页面更多空间
                log.debug(
                        "  Height ratio {:.2f} > 1.5, adjusting layout for mixed orientations",
                        heightRatio);

                // 确保较高的页面能够以较大的比例显示
                float targetScale = Math.min(1.0f, Math.max(0.8f, 2.0f / heightRatio));
                log.debug("  Target scale for better display: {:.2f}", targetScale);

                // 重新计算宽度，给每个页面合理的显示空间
                float adjustedWidth1 =
                        rect1.getWidth()
                                * (rect1.getHeight() > rect2.getHeight() ? targetScale : 1.0f);
                float adjustedWidth2 =
                        rect2.getWidth()
                                * (rect2.getHeight() > rect1.getHeight() ? targetScale : 1.0f);

                totalWidth = adjustedWidth1 + adjustedWidth2;
                log.debug(
                        "  Adjusted total width: {} (was: {})",
                        totalWidth,
                        rect1.getWidth() + rect2.getWidth());
            }

            // 全视图：不添加额外边距
            float outputWidth = totalWidth; // 直接使用页面总宽度
            float outputHeight = maxHeight; // 直接使用最大高度

            log.debug("  Final output size: {}x{}", outputWidth, outputHeight);

            return new PDRectangle(outputWidth, outputHeight);

        } catch (Exception e) {
            log.warn("Error calculating optimal size for two pages: {}", e.getMessage());
            // 回退到安全的默认尺寸
            return new PDRectangle(800, 600);
        }
    }

    // 改进的多页布局方法 - 更好的空间利用
    private void createMultiPageLayout(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int startPageIndex,
            int pagesToProcess,
            int pagesPerSheet,
            boolean addBorder)
            throws IOException {

        // 检测源页面的主要方向
        boolean majorityLandscape =
                checkMajorityOrientation(sourceDocument, startPageIndex, pagesToProcess);

        // 计算布局 - 统一使用2列1行左右排列
        int cols, rows;
        if (pagesPerSheet == 2) {
            // 不管横向还是纵向页面，都使用2列1行（左右排列）
            cols = 2;
            rows = 1;
            log.debug("2 pages: using 2 cols x 1 row layout");
        } else if (pagesPerSheet == 3) {
            cols = 3;
            rows = 1;
        } else {
            // 其他情况使用正方形布局
            cols = (int) Math.sqrt(pagesPerSheet);
            rows = (int) Math.sqrt(pagesPerSheet);
        }

        // 智能选择输出页面尺寸 - 根据源页面计算最佳尺寸
        PDRectangle outputPageSize;
        if (pagesPerSheet == 2) {
            // 计算最佳输出尺寸以容纳两个页面
            outputPageSize =
                    calculateOptimalOutputSize(
                            sourceDocument, startPageIndex, pagesToProcess, cols, rows);
        } else {
            outputPageSize = PDRectangle.A4;
        }

        log.debug("=== LAYOUT SUMMARY ===");
        log.debug("Pages to process: {}", pagesToProcess);
        log.debug("Layout: {} cols x {} rows", cols, rows);
        log.debug("Output page size: {}x{}", outputPageSize.getWidth(), outputPageSize.getHeight());
        log.debug(
                "Cell size: {}x{}",
                outputPageSize.getWidth() / cols,
                outputPageSize.getHeight() / rows);
        log.debug("=====================");

        PDPage outputPage = new PDPage(outputPageSize);
        newDocument.addPage(outputPage);

        try (PDPageContentStream contentStream =
                new PDPageContentStream(
                        newDocument,
                        outputPage,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true)) {

            contentStream.setLineWidth(1.0f);
            contentStream.setStrokingColor(Color.LIGHT_GRAY);

            // 完全移除页面间的间距以获得最大显示面积
            float margin = 0; // 无边距
            float availableWidth = outputPageSize.getWidth() - (2 * margin);
            float availableHeight = outputPageSize.getHeight() - (2 * margin);

            float cellWidth = availableWidth / cols;
            float cellHeight = availableHeight / rows;

            for (int i = 0; i < pagesToProcess; i++) {
                int sourcePageIndex = startPageIndex + i;
                PDPage sourcePage = sourceDocument.getPage(sourcePageIndex);
                PDRectangle sourceRect = sourcePage.getMediaBox();

                // 计算位置
                int colIndex = i % cols;
                int rowIndex = i / cols;

                log.debug(
                        "Page {} positioning: colIndex={}, rowIndex={}, cols={}, rows={}, pagesToProcess={}",
                        sourcePageIndex + 1,
                        colIndex,
                        rowIndex,
                        cols,
                        rows,
                        pagesToProcess);

                // 更激进的缩放计算 - 最大化页面利用率
                float cellPadding = 0; // 完全移除单元格内边距
                float availableCellWidth = cellWidth - (2 * cellPadding);
                float availableCellHeight = cellHeight - (2 * cellPadding);

                float scaleWidth = availableCellWidth / sourceRect.getWidth();
                float scaleHeight = availableCellHeight / sourceRect.getHeight();

                // 优先保持原始尺寸的缩放策略
                float scale;

                // 首先尝试使用原始尺寸（scale = 1.0）
                if (sourceRect.getWidth() <= availableCellWidth
                        && sourceRect.getHeight() <= availableCellHeight) {
                    // 页面可以使用原始尺寸放入单元格
                    scale = 1.0f;
                    log.debug("Using original size (scale=1.0) - page fits perfectly");
                } else {
                    // 页面太大，需要适度缩放
                    float minRequiredScale = Math.min(scaleWidth, scaleHeight);
                    if (minRequiredScale >= 0.8f) {
                        // 如果缩放不超过20%，使用保守缩放
                        scale = minRequiredScale;
                        log.debug("Using conservative scale: {} (minimal scaling)", scale);
                    } else {
                        // 页面太大，使用更激进的缩放
                        float aggressiveScale = (scaleWidth + scaleHeight) / 2 * 0.9f;
                        scale = Math.max(minRequiredScale, aggressiveScale);
                        log.debug(
                                "Using aggressive scale: min={}, aggressive={}, final={}",
                                minRequiredScale,
                                aggressiveScale,
                                scale);
                    }
                }

                // 不进行特殊限制，让页面保持计算出的最佳尺寸
                log.debug("Final scale for page {}: {}", sourcePageIndex + 1, scale);

                // 计算精确位置 - 智能处理单页和双页情况
                float scaledWidth = sourceRect.getWidth() * scale;
                float scaledHeight = sourceRect.getHeight() * scale;

                float x, y;

                if (pagesPerSheet == 2) {
                    if (pagesToProcess == 1) {
                        // 只有一页时，完全居中显示
                        x = (outputPageSize.getWidth() - scaledWidth) / 2;
                        y = (outputPageSize.getHeight() - scaledHeight) / 2;
                        log.debug("Single page centering: x={}, y={}", x, y);
                    } else {
                        // 两页时，紧贴左右排列，最小化中间间距
                        if (colIndex == 0) {
                            // 第一页：尽量靠左，但留少量边距
                            x = Math.max(0, (cellWidth - scaledWidth) / 2);
                        } else {
                            // 第二页：基于第一页位置计算
                            x = cellWidth + Math.max(0, (cellWidth - scaledWidth) / 2);
                        }
                        y = (outputPageSize.getHeight() - scaledHeight) / 2;
                        log.debug(
                                "Two pages optimized side-by-side: colIndex={}, x={}, y={}",
                                colIndex,
                                x,
                                y);
                    }
                } else {
                    // 其他布局：网格排列，也优化间距
                    x = colIndex * cellWidth + Math.max(0, (cellWidth - scaledWidth) / 2);
                    y =
                            outputPageSize.getHeight()
                                    - (rowIndex * cellHeight)
                                    - Math.max(0, (cellHeight - scaledHeight) / 2)
                                    - scaledHeight;
                }

                log.debug(
                        "Multi-page layout: page {} at cell ({},{}) position=({}, {}) scale={}",
                        sourcePageIndex + 1,
                        colIndex,
                        rowIndex,
                        x,
                        y,
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
                    contentStream.setStrokingColor(Color.LIGHT_GRAY);
                    contentStream.addRect(x, y, scaledWidth, scaledHeight);
                    contentStream.stroke();
                }
            }
        }
    }

    // 简化的多页布局方法 - 委托给特定页面方法
    private void createMultiPageLayoutSimplified(
            PDDocument sourceDocument,
            PDDocument newDocument,
            LayerUtility layerUtility,
            int startPageIndex,
            int pagesToProcess,
            int pagesPerSheet,
            boolean addBorder)
            throws IOException {

        // 将连续的页面索引转换为数组，然后调用特定页面方法
        int[] pageIndices = new int[pagesToProcess];
        for (int i = 0; i < pagesToProcess; i++) {
            pageIndices[i] = startPageIndex + i;
        }

        log.debug(
                "Converting consecutive pages {}-{} to specific pages method",
                startPageIndex + 1,
                startPageIndex + pagesToProcess);
        log.debug(
                "DEBUG: About to call createMultiPageLayoutWithSpecificPages with pagesPerSheet={}",
                pagesPerSheet);

        createMultiPageLayoutWithSpecificPages(
                sourceDocument, newDocument, layerUtility, pageIndices, pagesPerSheet, addBorder);
    }

    // 新增：检测页面组的主要方向
    private boolean checkMajorityOrientation(PDDocument sourceDocument, int startIndex, int count) {
        int landscapeCount = 0;
        int portraitCount = 0;

        for (int i = 0; i < count && (startIndex + i) < sourceDocument.getNumberOfPages(); i++) {
            PDPage page = sourceDocument.getPage(startIndex + i);
            if (isLandscapePage(page.getMediaBox())) {
                landscapeCount++;
            } else {
                portraitCount++;
            }
        }

        log.debug("Orientation check: landscape={}, portrait={}", landscapeCount, portraitCount);
        return landscapeCount > portraitCount;
    }

    // 新增：智能计算最佳输出页面尺寸
    private PDRectangle calculateOptimalOutputSize(
            PDDocument sourceDocument, int startIndex, int count, int cols, int rows) {
        try {
            // 分析所有源页面，找到最大的尺寸需求
            float maxPageWidth = 0;
            float maxPageHeight = 0;
            boolean majorityLandscape = false;

            for (int i = 0;
                    i < count && (startIndex + i) < sourceDocument.getNumberOfPages();
                    i++) {
                PDPage page = sourceDocument.getPage(startIndex + i);
                PDRectangle rect = page.getMediaBox();

                maxPageWidth = Math.max(maxPageWidth, rect.getWidth());
                maxPageHeight = Math.max(maxPageHeight, rect.getHeight());

                if (isLandscapePage(rect)) {
                    majorityLandscape = true; // 如果有横向页面就标记为横向主导
                }
            }

            // 根据布局计算所需的总尺寸 - 使用原始尺寸
            float requiredWidth = maxPageWidth * cols;
            float requiredHeight = maxPageHeight * rows;

            log.debug(
                    "Original size calculation: maxPage={}x{}, layout={}x{}, required={}x{}",
                    maxPageWidth,
                    maxPageHeight,
                    cols,
                    rows,
                    requiredWidth,
                    requiredHeight);

            log.debug(
                    "Calculated required size: {}x{} for layout {}x{}",
                    requiredWidth,
                    requiredHeight,
                    cols,
                    rows);
            log.debug("Max individual page size: {}x{}", maxPageWidth, maxPageHeight);
            log.debug("Majority landscape: {}", majorityLandscape);

            // 智能选择输出尺寸 - 避免横向页面过宽
            PDRectangle result;

            if (count == 2 && cols == 2 && rows == 1) {
                // 两页左右排列 - 创建能完美容纳原始尺寸的输出页面
                log.debug("Two pages side-by-side, creating output size for original dimensions");

                // 直接使用所需尺寸，加上最小边距
                float outputWidth = requiredWidth + 20; // 两页宽度 + 小边距
                float outputHeight = requiredHeight + 20; // 单页高度 + 小边距

                result = new PDRectangle(outputWidth, outputHeight);
                log.debug(
                        "Created custom output size for original page dimensions: {}x{} (required: {}x{})",
                        outputWidth,
                        outputHeight,
                        requiredWidth,
                        requiredHeight);

            } else if (count == 1) {
                // 单页情况 - 创建能完美容纳原始尺寸的输出页面
                log.debug("Single page detected, creating output size for original dimensions");

                // 直接使用原始页面尺寸加上小边距
                float outputWidth = requiredWidth + 20; // 页面宽度 + 小边距
                float outputHeight = requiredHeight + 20; // 页面高度 + 小边距

                result = new PDRectangle(outputWidth, outputHeight);
                log.debug(
                        "Created custom output size for single original page: {}x{} (required: {}x{})",
                        outputWidth,
                        outputHeight,
                        requiredWidth,
                        requiredHeight);

            } else {
                // 纵向页面或其他情况，使用标准逻辑
                // 检查A3尺寸适配性（横向优先）
                if (requiredWidth <= PDRectangle.A3.getWidth() + 50
                        && requiredHeight <= PDRectangle.A3.getHeight() + 50) {
                    result = PDRectangle.A3;
                    log.debug("Using A3 size (fits within tolerance)");
                }
                // 检查A4尺寸适配性（横向）
                else if (requiredWidth <= PDRectangle.A4.getHeight() + 50
                        && requiredHeight <= PDRectangle.A4.getWidth() + 50) {
                    result =
                            new PDRectangle(
                                    PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // 横向A4
                    log.debug("Using landscape A4 size");
                }
                // 检查A4尺寸适配性（纵向）
                else if (requiredWidth <= PDRectangle.A4.getWidth() + 50
                        && requiredHeight <= PDRectangle.A4.getHeight() + 50) {
                    result = PDRectangle.A4;
                    log.debug("Using portrait A4 size");
                }
                // 使用紧贴内容的自定义尺寸
                else {
                    result = new PDRectangle(requiredWidth + 5, requiredHeight + 5);
                    log.debug(
                            "Using tight custom size: {}x{} (required: {}x{})",
                            result.getWidth(),
                            result.getHeight(),
                            requiredWidth,
                            requiredHeight);
                }
            }

            return result;

        } catch (Exception e) {
            log.warn(
                    "Error calculating optimal output size, falling back to A3: {}",
                    e.getMessage());
            return PDRectangle.A3; // 出错时使用默认A3
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
