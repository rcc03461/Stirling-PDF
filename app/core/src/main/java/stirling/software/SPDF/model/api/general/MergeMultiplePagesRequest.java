package stirling.software.SPDF.model.api.general;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import stirling.software.common.model.api.PDFFile;

@Data
@EqualsAndHashCode(callSuper = true)
public class MergeMultiplePagesRequest extends PDFFile {

    @Schema(
            description = "The number of pages to fit onto a single sheet in the output PDF.",
            type = "number",
            defaultValue = "2",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"2", "3", "4", "9", "16"})
    private int pagesPerSheet;

    @Schema(description = "Boolean for if you wish to add border around the pages")
    private Boolean addBorder;

    @Schema(
            description =
                    "Comma-separated list of original PDF page numbers (1-based) that should keep their output page in A4 container size. For example: '1,3,5' means any output page containing original pages 1, 3, or 5 will be A4 size while others use A3 for 2 pages per sheet. Leave empty to use A3 for all output pages when 2 pages per sheet is selected.",
            example = "1,3,5")
    private String keepA4Pages;
}
