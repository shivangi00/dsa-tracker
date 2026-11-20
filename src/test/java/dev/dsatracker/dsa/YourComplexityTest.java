package dev.dsatracker.dsa;

import dev.dsatracker.dsa.analysis.ComplexityAnalysis;
import dev.dsatracker.shared.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YourComplexityTest {

    @Test
    void tidiesWhatYouType() {
        assertEquals("O(n log n)", YourComplexity.tidy("n log n", "time"));
        assertEquals("O(n)", YourComplexity.tidy("  o(n) ", "time"));
        assertEquals("O(m·n)", YourComplexity.tidy("O (m·n)", "time"));
        assertEquals("O(log(min(m, n)))", YourComplexity.tidy("O(log(min(m, n)))", "time"));
        assertEquals("O(n²)", YourComplexity.tidy("n²", "time"));
        assertEquals("O(n^2)", YourComplexity.tidy("n^2", "time"));
        assertNull(YourComplexity.tidy("   ", "time"));
        assertNull(YourComplexity.tidy(null, "space"));
    }

    @Test
    void rejectsWhatIsntAComplexity() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> YourComplexity.tidy("fast", "time"));
        assertTrue(e.getMessage().contains("O(n log n)"), e.getMessage());
        assertThrows(BadRequestException.class, () -> YourComplexity.tidy("O(n", "space"));
        assertThrows(BadRequestException.class, () -> YourComplexity.tidy("n".repeat(70), "time"));
    }

    @Test
    void saysWhenTheEstimateDisagrees() {
        var yours = analysis("O(n log k)", "O(log k)", ComplexityAnalysis.YOURS);
        assertNull(YourComplexity.estimateDiffers(yours, analysis("O(n log k)", "O(log k)", ComplexityAnalysis.ESTIMATE)));
        assertNull(YourComplexity.estimateDiffers(yours, null));
        String msg = YourComplexity.estimateDiffers(yours, analysis("O(n²)", "O(log k)", ComplexityAnalysis.ESTIMATE));
        assertTrue(msg.startsWith("The built-in estimate says O(n²) time."), msg);
        msg = YourComplexity.estimateDiffers(yours, analysis("O(n log k)", "O(n)", ComplexityAnalysis.ESTIMATE));
        assertTrue(msg.startsWith("The built-in estimate says O(n) space."), msg);
    }

    private static ComplexityAnalysis analysis(String time, String space, String source) {
        return new ComplexityAnalysis(time, space, List.of(), "high", source);
    }
}
