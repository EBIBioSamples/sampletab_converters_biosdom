package uk.ac.ebi.fgpt.sampletab.zooma;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.persistence.hibernate.application_mgmt.ExpPropValDAO;
import uk.ac.ebi.fg.core_model.expgraph.properties.BioCharacteristicValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.IdentifiableDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fgpt.sampletab.AbstractDriver;
import uk.ac.ebi.fgpt.zooma.model.AnnotationSummary;

public class DbMapThreadDriver extends AbstractDriver {

    private Logger log = LoggerFactory.getLogger(getClass());
        
    @Option(name = "--url", usage = "zooma url")
    private String zoomaUrl = "http://wwwdev.ebi.ac.uk/fgpt/zooma/";
    
    @Option(name = "--threads", usage = "no. of threads")
    private int threads = 12;
    
    private ZoomaUtil zoomaUtil;
    
    
    public static void main(String[] args) {
        new DbMapThreadDriver().doMain(args);
    }
    
    
    public void doMain(String[] args) {
        super.doMain(args);
        
        try {
            zoomaUtil = new ZoomaUtil(new URL(zoomaUrl));
        } catch (MalformedURLException e) {
            log.error("Invalid zooma url "+zoomaUrl, e);
            return;
        }

        EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
        

//        Properties hibernateProperties = new Properties ();
//        ClassLoader loader = Thread.currentThread().getContextClassLoader();
//        try {
//            hibernateProperties.load ( loader.getResourceAsStream ( "hibernate_bioSD.properties"));
//        } catch (IOException e) {
//            log.error("unable to read hibernate_bioSD.properties", e);
//            return;
//        }
//        EntityManagerFactory emf = Persistence.createEntityManagerFactory ( "defaultPersistenceUnit" , hibernateProperties);
        
        EntityManager em = emf.createEntityManager();
        EntityTransaction tns = em.getTransaction();

        ExpPropValDAO expPropValDAO = new ExpPropValDAO( em );
        

        int startId = 0;
        int chunkSize = 500;
        List<ExperimentalPropertyValue<ExperimentalPropertyType>> results = null;
        
        List<Future<AnnotationSummary>> futures = new ArrayList<Future<AnnotationSummary>>();
        
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        
        while (results == null || results.size() == chunkSize) {
            tns.begin ();
            long time0 = System.currentTimeMillis();
            results = expPropValDAO.getUnmapped(startId, chunkSize);
            startId += chunkSize;
            long time1 = System.currentTimeMillis();
            System.out.println("Time to find property values: "+(time1-time0));
            System.out.println("Cache hit rate: "+zoomaUtil.cacheHitRate());
            
            for (ExperimentalPropertyValue<ExperimentalPropertyType> value : results) {      
                String typeStr = value.getType().getTermText();
                String valueStr = value.getTermText();
                                
                ZoomaCallable callable = new ZoomaCallable(typeStr, valueStr);
                futures.add(pool.submit(callable));
            }
            
            for (Future<AnnotationSummary> future : futures) {
                AnnotationSummary zoomaResult = null;
                try {
                    zoomaResult = future.get(60, TimeUnit.SECONDS);
                } catch (Throwable e) {
                    log.error("Problem accessing Zooma", e);
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
            tns.commit ();
            futures.clear();
            
        }
        
        pool.shutdown();
    }
    
    public class ZoomaCallable implements Callable<AnnotationSummary> {

        private final String type;
        private final String value;
        
        public ZoomaCallable(String type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public AnnotationSummary call() throws Exception {
            return zoomaUtil.processCached(type, value);
        }
        
    }
}
