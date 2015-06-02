package org.verapdf.integration.pdfa_1.b_6_1_12_implementationlimits;

import org.junit.Assert;
import org.junit.Test;
import org.verapdf.integration.base.BasePDFAIT;

/**
 * Implementation limits - min Integer value test
 */
public class VeraPDF_6_1_12_t01_fail_a_IT extends BasePDFAIT {

    @Test
    public void testValidation() throws Exception {
        Assert.assertTrue(testValidationSuccessful());
    }

    @Override
    protected String getExpectedReportFilePath() throws Exception {
        return getSystemIndependentPath("/testfiles/pdfa_1/b_6_1_12_implementationlimits/1/6-1-12-t01-fail-a-report.xml");
    }

    @Override
    protected String getValidationProfileFilePath() throws Exception {
        return getSystemIndependentPath(VALIDATION_PROFILES_REPO_NAME + "PDF_A/1b/6.1 File structure/6.1.12 Implementation limits/verapdf-profile-6-1-12-t01-b.xml");
    }

    @Override
    protected String getPdfFilePath() throws Exception {
        return getSystemIndependentPath(TEST_FILES_REPO_NAME + "PDF_A-1b/6.1 File structure/6.1.12 Implementation limits/veraPDF test suite 6-1-12-t01-fail-a.pdf");
    }

}