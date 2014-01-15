/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataTable;
import edu.harvard.iq.dataverse.datavariable.DataVariable;
import edu.harvard.iq.dataverse.datavariable.VariableRange;
import edu.harvard.iq.dataverse.datavariable.VariableServiceBean;
import edu.harvard.iq.dataverse.datavariable.SummaryStatistic;
import edu.harvard.iq.dataverse.datavariable.VariableCategory;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import javax.ejb.Stateless;
import javax.inject.Named;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLOutputFactory;



/**
 *
 * @author Leonid Andreev
 * 
 * Draft/prototype DDI export service for DVN 4.0
 * 
 * Note that this is definitely a "prototype". One of the stated dev. goals of
 * 4.0 is to have export/import services that utilize application-defined 
 * metadata schemas and cross-schema mappings. But since this new architecture
 * hasn't been finalized yet, this version follows the v2-3 scheme of using
 * programmed/hard-coded metadata fields and formatting. 
 */

@Stateless
@Named
public class DDIExportServiceBean {
    private static final Logger logger = Logger.getLogger(DDIExportServiceBean.class.getCanonicalName());
    @EJB
    DatasetServiceBean datasetService;
    
    @EJB 
    DataFileServiceBean fileService;
    
    @EJB
    VariableServiceBean variableService;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;
    
    /*
     * Constants used by the worker methods:
     */
     private static final String OBJECT_TAG_VARIABLE = "variable";
     private static final String OBJECT_TAG_DATAFILE = "datafile";
     private static final String OBJECT_TAG_DATASET  = "dataset";
    /*
     * Database and schema-specific constants:
     * Needless to say, we should *not* be defining these here - it should
     * all live in the database somewhere/somehow.
     */
    public static final String DB_VAR_INTERVAL_TYPE_CONTINUOUS = "continuous";
    public static final String VAR_INTERVAL_CONTIN = "contin";
    public static final String DB_VAR_RANGE_TYPE_POINT = "point";
    public static final String DB_VAR_RANGE_TYPE_MIN = "min";
    public static final String DB_VAR_RANGE_TYPE_MIN_EX = "min exclusive";
    public static final String DB_VAR_RANGE_TYPE_MAX = "max";
    public static final String DB_VAR_RANGE_TYPE_MAX_EX = "max exclusive";
    public static final String LEVEL_FILE = "file";
    public static final String NOTE_TYPE_UNF = "VDC:UNF";
    public static final String NOTE_SUBJECT_UNF = "Universal Numeric Fingerprint";
    
    /*
     * Internal service objects:
     */
    private XMLOutputFactory xmlOutputFactory = null;
    
    public void ejbCreate() {
        // initialize lists/service classes:

        xmlOutputFactory = javax.xml.stream.XMLOutputFactory.newInstance();
    }
    
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void exportDataVariable(Long varId, OutputStream os, String partialExclude, String partialInclude) {
        
        export (OBJECT_TAG_VARIABLE, varId, os, partialExclude, partialInclude); 
    }
    
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void exportDataset(Dataset s, OutputStream os) {
        
    }
    
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void exportDataFile(Long varId, OutputStream os, String partialExclude, String partialInclude) {
        export (OBJECT_TAG_DATAFILE, varId, os, partialExclude, partialInclude);
        
    }
    
    /*
     * Workhorse methods, that do all the work: 
     */
    
    private void export(String objectTag, Long objectId, OutputStream os, String partialExclude, String partialInclude) {
        
        /*
         * Some checks will need to be here, to see if the corresponding dataset
         * is released, if all the permissions are satisfied, etc., with 
         * approrpiate exceptions thrown otherwise. 
         *
         *      something like
        
            throw new IllegalArgumentException("ExportStudy called with a null study.");
            throw new IllegalArgumentException("Study does not have released version, study.id = " + s.getId());
        */
        
        
        Set<String> includedFieldSet = null; 
        Set<String> excludedFieldSet = null; 
        
        if (partialExclude != null && !"".equals(partialExclude)) {
            excludedFieldSet = new HashSet<String>();
            
            String splitTokens[] = partialExclude.split(",");
            
            for (int i = 0; i < splitTokens.length; i++) {
                if (splitTokens[i] != null && !splitTokens[i].equals("")) {
                    excludedFieldSet.add(splitTokens[i]);
                }
            }
        }
        
        if (partialInclude != null && !"".equals(partialInclude)) {
            includedFieldSet = new HashSet<String>();
            
            String splitTokens[] = partialInclude.split(",");
            
            for (int i = 0; i < splitTokens.length; i++) {
                if (splitTokens[i] != null && !splitTokens[i].equals("")) {
                    includedFieldSet.add(splitTokens[i]);
                }
            }
        } 
        
        // Create XML Stream Writer, using the supplied OutputStream:
        
        XMLStreamWriter xmlw = null; 
        
        
        
        // Try to resolve the supplied object id: 
        
        Object dataObject = null; 
        
        if (OBJECT_TAG_VARIABLE.equals(objectTag)) {
            dataObject = variableService.find(objectId);
            if (dataObject == null) {
                throw new IllegalArgumentException("Metadata Export: Invalid variable id supplied.");
            }
        } else if (OBJECT_TAG_DATAFILE.equals(objectTag)) {
            dataObject = fileService.find(objectId); 
            if (dataObject == null) {
                throw new IllegalArgumentException("Metadata Export: Invalid datafile id supplied.");
            }
        } else {
            throw new IllegalArgumentException("Metadata Export: Unsupported export requested.");
        }
        
        try {
            xmlw = xmlOutputFactory.createXMLStreamWriter(os);
            xmlw.writeStartDocument();
            
            if (OBJECT_TAG_VARIABLE.equals(objectTag)) {
                createVar(xmlw, excludedFieldSet, includedFieldSet, (DataVariable)dataObject);
            }
            
            xmlw.writeEndDocument();
        } catch (XMLStreamException ex) {
            Logger.getLogger("global").log(Level.SEVERE, null, ex);
            throw new EJBException("ERROR occurred during partial export of a study.", ex);
        } finally {
            try {
                if (xmlw != null) {
                    xmlw.close();
                }
            } catch (XMLStreamException ex) {
            }
        }    
    }
    
    private void createVar(XMLStreamWriter xmlw, Set<String> excludedFieldSet, Set<String> includedFieldSet, DataVariable dv) throws XMLStreamException {
        xmlw.writeStartElement("var");
        writeAttribute( xmlw, "ID", "v" + dv.getId().toString() );
        writeAttribute( xmlw, "name", dv.getName() );

        if (dv.getNumberOfDecimalPoints() != null) {
            writeAttribute(xmlw, "dcml", dv.getNumberOfDecimalPoints().toString() );
        }

        if (dv.getVariableIntervalType() != null) {
            String interval = dv.getVariableIntervalType().getName();
            interval = DB_VAR_INTERVAL_TYPE_CONTINUOUS.equals(interval) ? VAR_INTERVAL_CONTIN : interval;
            writeAttribute( xmlw, "intrvl", interval );
        }

        
        // location
        
        if (checkField("location", excludedFieldSet, includedFieldSet)) {
                xmlw.writeEmptyElement("location");
                if (dv.getFileStartPosition() != null) writeAttribute( xmlw, "StartPos", dv.getFileStartPosition().toString() );
                if (dv.getFileEndPosition() != null) writeAttribute( xmlw, "EndPos", dv.getFileEndPosition().toString() );
                if (dv.getRecordSegmentNumber() != null) writeAttribute( xmlw, "RecSegNo", dv.getRecordSegmentNumber().toString());
        
                writeAttribute( xmlw, "fileid", "f" + dv.getDataTable().getDataFile().getId().toString() );
        }

        // labl
        if (checkField("labl", excludedFieldSet, includedFieldSet)) {
            if (!StringUtilisEmpty(dv.getLabel())) {
                xmlw.writeStartElement("labl");
                writeAttribute(xmlw, "level", "variable");
                xmlw.writeCharacters(dv.getLabel());
                xmlw.writeEndElement(); //labl
            }
        }

        // invalrng
        if (checkField("invalrng", excludedFieldSet, includedFieldSet)) {
            boolean invalrngAdded = false;
            for (VariableRange range : dv.getInvalidRanges()) {
                if (range.getBeginValueType() != null && range.getBeginValueType().getName().equals(DB_VAR_RANGE_TYPE_POINT)) {
                    if (range.getBeginValue() != null) {
                        invalrngAdded = checkParentElement(xmlw, "invalrng", invalrngAdded);
                        xmlw.writeEmptyElement("item");
                        writeAttribute(xmlw, "VALUE", range.getBeginValue());
                    }
                } else {
                    invalrngAdded = checkParentElement(xmlw, "invalrng", invalrngAdded);
                    xmlw.writeEmptyElement("range");
                    if (range.getBeginValueType() != null && range.getBeginValue() != null) {
                        if (range.getBeginValueType().getName().equals(DB_VAR_RANGE_TYPE_MIN)) {
                            writeAttribute(xmlw, "min", range.getBeginValue());
                        } else if (range.getBeginValueType().getName().equals(DB_VAR_RANGE_TYPE_MIN_EX)) {
                            writeAttribute(xmlw, "minExclusive", range.getBeginValue());
                        }
                    }
                    if (range.getEndValueType() != null && range.getEndValue() != null) {
                        if (range.getEndValueType().getName().equals(DB_VAR_RANGE_TYPE_MAX)) {
                            writeAttribute(xmlw, "max", range.getEndValue());
                        } else if (range.getEndValueType().getName().equals(DB_VAR_RANGE_TYPE_MAX_EX)) {
                            writeAttribute(xmlw, "maxExclusive", range.getEndValue());
                        }
                    }
                }
            }
            if (invalrngAdded) {
                xmlw.writeEndElement(); // invalrng
            }
        }

        //universe
        if (checkField("universe", excludedFieldSet, includedFieldSet)) {
            if (!StringUtilisEmpty(dv.getUniverse())) {
                xmlw.writeStartElement("universe");
                xmlw.writeCharacters(dv.getUniverse());
                xmlw.writeEndElement(); //universe
            }
        }

        //sum stats
        if (checkField("sumStat", excludedFieldSet, includedFieldSet)) {
            for (SummaryStatistic sumStat : dv.getSummaryStatistics()) {
                xmlw.writeStartElement("sumStat");
                writeAttribute(xmlw, "type", sumStat.getType().getName());
                xmlw.writeCharacters(sumStat.getValue());
                xmlw.writeEndElement(); //sumStat
            }
        }

        // categories
        if (checkField("catgry", excludedFieldSet, includedFieldSet)) {
            for (VariableCategory cat : dv.getCategories()) {
                xmlw.writeStartElement("catgry");
                if (cat.isMissing()) {
                    writeAttribute(xmlw, "missing", "Y");
                }

                // catValu
                xmlw.writeStartElement("catValu");
                xmlw.writeCharacters(cat.getValue());
                xmlw.writeEndElement(); //catValu

                // label
                if (!StringUtilisEmpty(cat.getLabel())) {
                    xmlw.writeStartElement("labl");
                    writeAttribute(xmlw, "level", "category");
                    xmlw.writeCharacters(cat.getLabel());
                    xmlw.writeEndElement(); //labl
                }

                // catStat
                if (cat.getFrequency() != null) {
                    xmlw.writeStartElement("catStat");
                    writeAttribute(xmlw, "type", "freq");
                    // if frequency is actually a long value, we want to write "100" instead of "100.0"
                    if (Math.floor(cat.getFrequency()) == cat.getFrequency()) {
                        xmlw.writeCharacters(new Long(cat.getFrequency().longValue()).toString());
                    } else {
                        xmlw.writeCharacters(cat.getFrequency().toString());
                    }
                    xmlw.writeEndElement(); //catStat
                }

                xmlw.writeEndElement(); //catgry
            }
        }

        // varFormat
        if (checkField("varFormat", excludedFieldSet, includedFieldSet)) {
            xmlw.writeEmptyElement("varFormat");
            writeAttribute(xmlw, "type", dv.getVariableFormatType().getName());
            writeAttribute(xmlw, "formatname", dv.getFormatSchemaName());
            writeAttribute(xmlw, "schema", dv.getFormatSchema());
            writeAttribute(xmlw, "category", dv.getFormatCategory());
        }

        // notes
        if (checkField("unf", excludedFieldSet, includedFieldSet)) {
            xmlw.writeStartElement("notes");
            writeAttribute(xmlw, "subject", "Universal Numeric Fingerprint");
            writeAttribute(xmlw, "level", "variable");
            writeAttribute(xmlw, "type", "VDC:UNF");
            xmlw.writeCharacters(dv.getUnf());
            xmlw.writeEndElement(); //notes

            xmlw.writeEndElement(); //var
        }
    }
    
    private void createDataFile(XMLStreamWriter xmlw, Set<String> excludedFieldSet, Set<String> includedFieldSet, DataFile df) throws XMLStreamException {
        /* This method will create both the <fileDscr> and <dataDscr><var> 
         * portions of the DDI that describe the tabular data contained in 
         * the file, the file-, datatable- and variable-level metadata; or 
         * a subset of the above, as defined by the "include" and "exclude" 
         * parameters. 
         */
        
         DataTable dt = fileService.findDataTableByFileId(df.getId());
         createFileDscr(xmlw, excludedFieldSet, includedFieldSet, df, dt);
         
         List<DataVariable> vars = variableService.findByDataFileId(df.getId());
         
         for (DataVariable var : vars) {
             createVar(xmlw, excludedFieldSet, includedFieldSet, var);
         }
        
    }
    
    private void createFileDscr(XMLStreamWriter xmlw, Set<String> excludedFieldSet, Set<String> includedFieldSet, DataFile df, DataTable dt) throws XMLStreamException {
              

            xmlw.writeStartElement("fileDscr");
            writeAttribute( xmlw, "ID", "f" + df.getId().toString() );
            //writeAttribute( xmlw, "URI", determineFileURI(fm) );

            // fileTxt
            xmlw.writeStartElement("fileTxt");

            xmlw.writeStartElement("fileName");
            xmlw.writeCharacters( df.getName() );
            xmlw.writeEndElement(); // fileName

            /*
                xmlw.writeStartElement("fileCont");
                xmlw.writeCharacters( df.getContentType() );
                xmlw.writeEndElement(); // fileCont
            */

            // dimensions
            if (dt.getCaseQuantity() != null || dt.getVarQuantity() != null || dt.getRecordsPerCase() != null) {
                xmlw.writeStartElement("dimensns");

                if (dt.getCaseQuantity() != null) {
                    xmlw.writeStartElement("caseQnty");
                    xmlw.writeCharacters( dt.getCaseQuantity().toString() );
                    xmlw.writeEndElement(); // caseQnty
                }
                if (dt.getVarQuantity() != null) {
                    xmlw.writeStartElement("varQnty");
                    xmlw.writeCharacters( dt.getVarQuantity().toString() );
                    xmlw.writeEndElement(); // varQnty
                }
                if (dt.getRecordsPerCase() != null) {
                    xmlw.writeStartElement("recPrCas");
                    xmlw.writeCharacters( dt.getRecordsPerCase().toString() );
                    xmlw.writeEndElement(); // recPrCas
                }

                xmlw.writeEndElement(); // dimensns
            }

            xmlw.writeStartElement("fileType");
            xmlw.writeCharacters( df.getContentType() );
            xmlw.writeEndElement(); // fileType

            xmlw.writeEndElement(); // fileTxt

            // notes
            xmlw.writeStartElement("notes");
            writeAttribute( xmlw, "level", LEVEL_FILE );
            writeAttribute( xmlw, "type", NOTE_TYPE_UNF );
            writeAttribute( xmlw, "subject", NOTE_SUBJECT_UNF );
            xmlw.writeCharacters( dt.getUnf() );
            xmlw.writeEndElement(); // notes

            /*
                xmlw.writeStartElement("notes");
                writeAttribute( xmlw, "type", "vdc:category" );
                xmlw.writeCharacters( fm.getCategory() );
                xmlw.writeEndElement(); // notes
            */

            // A special note for LOCKSS crawlers indicating the restricted
            // status of the file:

            /*
                if (tdf != null && isRestrictedFile(tdf)) {
                    xmlw.writeStartElement("notes");
                    writeAttribute( xmlw, "type", NOTE_TYPE_LOCKSS_CRAWL );
                    writeAttribute( xmlw, "level", LEVEL_FILE );
                    writeAttribute( xmlw, "subject", NOTE_SUBJECT_LOCKSS_PERM );
                    xmlw.writeCharacters( "restricted" );
                    xmlw.writeEndElement(); // notes

            }
            */

            xmlw.writeEndElement(); // fileDscr   
    }
    
    /*
     * Helper/utility methods:
     */
    
    private void writeAttribute(XMLStreamWriter xmlw, String name, String value) throws XMLStreamException {
        if ( !StringUtilisEmpty(value) ) {
            xmlw.writeAttribute(name, value);
        }
    }
    
    private boolean checkParentElement(XMLStreamWriter xmlw, String elementName, boolean elementAdded) throws XMLStreamException {
        if (!elementAdded) {
            xmlw.writeStartElement(elementName);
        }

        return true;
    }
    
    private boolean checkField (String fieldName, Set<String> excludedFieldSet, Set<String> includedFieldSet) {
        
        if (includedFieldSet == null || includedFieldSet.contains(fieldName)) {
            if (excludedFieldSet == null || !excludedFieldSet.contains(fieldName)) {
                return true;
            }
        }
        return false;
    }
    
    /*
     * locally-defined "isEmpty" utility (was part of the "StringUtil" class
     * back in DVN v2-3).
     */
    private boolean StringUtilisEmpty(String str) {
        if (str==null || str.trim().equals("")) {
            return true;
        } 
        return false;
    }
    
}
