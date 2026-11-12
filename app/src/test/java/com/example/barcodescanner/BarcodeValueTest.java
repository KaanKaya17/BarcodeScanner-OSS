package com.example.barcodescanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BarcodeValueTest {
    @Test
    public void preservesLeadingZerosAndTrimsSurroundingSpaces() {
        assertEquals("0049000006346", BarcodeValue.normalize(" 0049000006346 "));
        assertEquals("0049000006346", BarcodeValue.normalize("049000006346"));
        assertEquals("96385074", BarcodeValue.normalize("96385074"));
        assertEquals("12345678901234", BarcodeValue.normalize("12345678901234"));
        assertNull(BarcodeValue.normalize("00000000"));
        assertNull(BarcodeValue.normalize("1234-5678"));
        assertNull(BarcodeValue.normalize("https://example.com"));
    }

    @Test
    public void expandsUpcEForLookup() {
        assertEquals("0042100005264", BarcodeValue.expandUpcE("04252614"));
        assertEquals("0012300000451", BarcodeValue.expandUpcE("01234531"));
        assertEquals("0012340000051", BarcodeValue.expandUpcE("01234541"));
        assertEquals("0012345000061", BarcodeValue.expandUpcE("01234561"));
        assertNull(BarcodeValue.expandUpcE("21234561"));
    }
}
