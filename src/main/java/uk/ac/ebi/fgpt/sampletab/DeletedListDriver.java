package uk.ac.ebi.fgpt.sampletab;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.application_mgmt.JobRegisterEntry;
import uk.ac.ebi.fg.biosd.model.application_mgmt.JobRegisterEntry.Operation;
import uk.ac.ebi.fg.biosd.model.persistence.hibernate.application_mgmt.JobRegisterDAO;
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

        EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
        EntityManager em = emf.createEntityManager();
        JobRegisterDAO jrDao = new JobRegisterDAO(em);
        

        Writer writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            process(jrDao, writer);
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
    
    private void process (JobRegisterDAO jrDao, Writer writer) throws IOException {
        for (JobRegisterEntry e : jrDao.find(fromDate, toDate, "BioSample", Operation.DELETE ) ) {
            writer.write(e.getAcc());
            writer.write("\n");
        }
    }
    
    
    public static void main(String[] args)  {
        new DeletedListDriver().doMain(args);

    }
}
