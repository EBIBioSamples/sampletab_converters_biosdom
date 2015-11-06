package uk.ac.ebi.fgpt.sampletab.zooma;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.persistence.hibernate.application_mgmt.ExpPropValDAO;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.sampletab.AbstractDriver;
import uk.ac.ebi.fgpt.zooma.model.AnnotationSummary;

public class DbMapDriver extends AbstractDriver {

    private Logger log = LoggerFactory.getLogger(getClass());
        
    @Option(name = "--url", usage = "zooma url")
    private String zoomaUrl = "http://wwwdev.ebi.ac.uk/fgpt/zooma/";
    
    private ZoomaUtil zoomaUtil;
    
    
    public static void main(String[] args) {
        new DbMapDriver().doMain(args);
    }
    
    
    public void doMain(String[] args) {
        super.doMain(args);
        
        try {
            zoomaUtil = new ZoomaUtil(new URL(zoomaUrl));
        } catch (MalformedURLException e) {
            log.error("Invalid zooma url "+zoomaUrl, e);
            return;
        }

        EntityManager em =  Resources.getInstance().getEntityManagerFactory().createEntityManager();
        ExpPropValDAO expPropValDAO = new ExpPropValDAO(em);
        EntityTransaction tns = em.getTransaction();
        
        tns.begin ();

        int startId = 0;
        int chunkSize = 500;
        List<ExperimentalPropertyValue<ExperimentalPropertyType>> results = null;
        while (results == null || results.size() == chunkSize) {
            long time0 = System.currentTimeMillis();
            results = expPropValDAO.getUnmapped(startId, chunkSize);
            startId += chunkSize;
            long time1 = System.currentTimeMillis();
            System.out.println("Time to find property values: "+(time1-time0));
            
            for (ExperimentalPropertyValue<ExperimentalPropertyType> value : results) {      
                String typeStr = value.getType().getTermText();
                String valueStr = value.getTermText();
                
                AnnotationSummary zoomaResult = null;
                
                try {
                    zoomaResult = zoomaUtil.processCached(typeStr, valueStr);
                } catch (Throwable e) {
                    log.error("Unable to process "+typeStr+":"+valueStr, e);
                    continue;
                }
                
                
                //if zooma produced a hit
                if (zoomaResult != null) {
                    //System.out.println(zoomaResult.getURI().toString());
                    
                    //OntologyEntry ontologyTerm = new OntologyEntry(zoomaResult.getURI().toString(), null);
                    //value.addOntologyTerm(ontologyTerm);
                    //valueDao.create(value);
                } else {
                    //System.out.println("no hit");
                }
            }
        }
        tns.commit ();
        
    }
}
