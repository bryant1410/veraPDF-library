package org.verapdf.model.impl.pb.cos;

import org.apache.log4j.Logger;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.verapdf.model.baselayer.Object;
import org.verapdf.model.coslayer.CosDocument;
import org.verapdf.model.coslayer.CosIndirect;
import org.verapdf.model.coslayer.CosTrailer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level PDF Document object
 *
 * @author Evgeniy Muravitskiy
 */
public class PBCosDocument extends PBCosObject implements CosDocument {

    private final static Logger logger = Logger.getLogger(PBCosDocument.class);

    public final static String TRAILER = "trailer";
    public final static String XREF = "xref";
    public final static String INDIRECT_OBJECTS = "indirectObjects";
    public final static String DOCUMENT = "document";

    private Integer sizeOfDocument = -1;

    public PBCosDocument(COSDocument baseObject) {
        super(baseObject);
        setType("CosDocument");
    }

    /**  Number of indirect objects in the document
     */
    @Override
    public Long getnrIndirects() {
        return (long) ((COSDocument) baseObject).getObjects().size();
    }

    /**  Size of the byte sequence representing the document
     */
    @Override
    public Long getsize() {
        return Long.valueOf(sizeOfDocument);
    }

    /**  true if the second line of the document is a comment with at least 4 symbols in the code range 128-255 as required by PDF/A standard
     */
    @Override
    public Boolean getbinaryHeaderComplyPDFA() {

        return !(((COSDocument) baseObject).getNonValidCommentContent() ||
                ((COSDocument) baseObject).getNonValidCommentLength() ||
                ((COSDocument) baseObject).getNonValidCommentStart());
    }

    /** true if first line of document complies PDF/A standard
     */
    @Override
    public Boolean getpdfHeaderCompliesPDFA() {
        return !((COSDocument) baseObject).getNonValidHeader();
    }

    /** true if catalog contain OCProperties key
     */
    @Override
    public Boolean getisOptionalContentPresent() {
        try {
            COSDictionary root = (COSDictionary) ((COSDocument) baseObject).getCatalog().getObject();
            return root.getItem(COSName.OCPROPERTIES) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** EOF must complies PDF/A standard
     */
    public Boolean geteofCompliesPDFA() {
        return ((COSDocument) baseObject).getEofComplyPDFA();
    }

    /**
     * @return true if XMP content matches Info dictionary content
     */
    // TODO : implement this
    public Boolean getdoesInfoMatchXMP() {
        return false;
    }
    @Override
    public List<? extends org.verapdf.model.baselayer.Object> getLinkedObjects(String link) {
        List<? extends org.verapdf.model.baselayer.Object> list;

        switch (link) {
            case TRAILER:
                list = this.getTrailer();
                break;
            case INDIRECT_OBJECTS:
                list = this.getIndirectObjects();
                break;
            case DOCUMENT:
                list = this.getDocument();
                break;
            case XREF:
                list = this.getXRef();
                break;
            default:
                list = super.getLinkedObjects(link);
        }

        return list;
    }

    /**  trailer dictionary
     */
    private List<CosTrailer> getTrailer() {
        List<CosTrailer> trailer = new ArrayList<>();
        trailer.add(new PBCosTrailer(((COSDocument) baseObject).getTrailer()));
        return trailer;
    }

    /**  all indirect objects referred from the xref table
     */
    private List<CosIndirect> getIndirectObjects() {
        List<CosIndirect> indirects = new ArrayList<>();
        for (COSBase object : ((COSDocument) baseObject).getObjects()) {
            indirects.add(new PBCosIndirect(object));
        }
        return indirects;
    }

    /**  link to the high-level PDF Document structure
     */
    // TODO : add support of this feature
    private List<Object> getDocument() {
        logger.warn("Trying get PDDocument from CosDocument.\r\n" +
                "Current feature not supported yet. Method always return null.");
        return null;
    }

    /** link to cross reference table properties
     */
    // TODO : add support of this feature
    private List<? extends Object> getXRef() {
        logger.warn("Xref not supported yes. return null");
        return null;
    }
}