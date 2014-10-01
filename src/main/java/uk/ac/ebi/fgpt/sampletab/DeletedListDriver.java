package uk.ac.ebi.fgpt.sampletab;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.application_mgmt.JobRegisterEntry;
import uk.ac.ebi.fg.biosd.model.application_mgmt.JobRegisterEntry.Operation;
import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.model.persistence.hibernate.application_mgmt.JobRegisterDAO;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.sampletab.AbstractDriver;
import uk.ac.ebi.fgpt.sampletab.subs.JobRegistryDriver;

public class DeletedListDriver extends AbstractDriver  {

    @Argument(required=true, index=0, metaVar="FROM", usage = "from date (yyyy/MM/dd)")
    protected String fromDateString;

    @Argument(required=true, index=1, metaVar="TO", usage = "to date (yyyy/MM/dd)")
    protected String toDateString;

    @Argument(required=true, index=2, metaVar="OUTPUT", usage = "output filename")
    protected File outputFile;


    Date fromDate = null;
    Date toDate = null;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
    
    private static Logger log =  LoggerFactory.getLogger(JobRegistryDriver.class); 

    public DeletedListDriver(){
        
    }
    
    @Override
    public void doMain(String[] args){
        super.doMain(args);
        try {
            fromDate = dateFormat.parse(fromDateString);
        } catch (ParseException e) {
            log.error("Unable to parse date "+fromDateString, e);
            return;
        }
        try {
            toDate = dateFormat.parse(toDateString);
        } catch (ParseException e) {
            log.error("Unable to parse date "+toDateString, e);
            return;
        }

        EntityManager em = Resources.getInstance().getEntityManagerFactory().createEntityManager();
        
        JobRegisterDAO jrDao = new JobRegisterDAO(em);   
        
        AccessibleDAO<BioSample> biosampleDAO = new AccessibleDAO<BioSample>(BioSample.class, em);    

        Writer writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            process(jrDao, biosampleDAO, writer);
        } catch (IOException e) {
            log.error("Unable to open "+outputFile+" for writing", e);
            return;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    //do nothing
                }
            }
        }        
    }
    
    private void process (JobRegisterDAO jrDao, AccessibleDAO<BioSample> biosampleDAO, Writer writer) throws IOException {     
        if (jrDao == null) throw new IllegalArgumentException("jrDao must not be null");
        if (biosampleDAO == null) throw new IllegalArgumentException("biosampleDAO must not be null");
        if (writer == null) throw new IllegalArgumentException("writer must not be null");        
        
        Map<String, Integer> counterMap = new HashMap<String, Integer>();
        for (JobRegisterEntry e : jrDao.find(fromDate, toDate, "BioSample") ) {
            if (e.getAcc() == null) continue;
            
            if (!counterMap.containsKey(e.getAcc())) {
                counterMap.put(e.getAcc(), 0);
            }
            
            if (e.getOperation() == Operation.DELETE) {
                counterMap.put(e.getAcc(), counterMap.get(e.getAcc())-1);
            } else if (e.getOperation() == Operation.ADD) {
                counterMap.put(e.getAcc(), counterMap.get(e.getAcc())+1);
            }
        }
        
        //all the entries in counterMap with a value of 0 or lower MAY
        //have been removed or been made private via release date update(s)
        Date now = new Date();
        
        for (String acc : counterMap.keySet()) {
            if (counterMap.get(acc) <= 0) {
                BioSample bs = biosampleDAO.find(acc);
                if (bs == null) {
                    //it was deleted entirely
                    writer.write(acc+"\n");
                    
                } else {
                    //get the release date for the sample
                    //or the release date of the submission of the sample
                    Date releaseDate = bs.getReleaseDate();
                    if (releaseDate == null){
                        log.warn("Sample "+acc+" has a null release date");
                        //TODO handle this better once MSI resolution is solved
                        for (MSI msi : bs.getMSIs()) {
                            if (msi.getReleaseDate() != null 
                                    && (releaseDate == null || releaseDate.after(msi.getReleaseDate()))) {
                                releaseDate = msi.getReleaseDate();
                            }
                        }
                    }
                    //if its release date is in the future
                    if (now.before(releaseDate)) {
                        writer.write(acc+"\n");
                    }
                }
            }
        }
        
    }
    
    
    public static void main(String[] args)  {
        new DeletedListDriver().doMain(args);

    }
}
