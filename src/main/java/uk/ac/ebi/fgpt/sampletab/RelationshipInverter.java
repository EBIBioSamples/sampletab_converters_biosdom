package uk.ac.ebi.fgpt.sampletab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.resources.Resources;

public class RelationshipInverter extends AbstractDriver {

    
    private EntityManager em = null;

    private static Logger log =  LoggerFactory.getLogger(RelationshipInverter.class); 
    
    @Override
    public void doMain(String[] args){
        super.doMain(args);

        em = Resources.getInstance().getEntityManagerFactory().createEntityManager();

        Set<List<String>> derivedFroms = getDerivedFrom();
        Set<List<String>> derivedTos = getDerivedFrom();
        
        //get all derived from without an inverse
        for (List<String> derivedFrom : derivedFroms) {
            List<String> derivedTo = new ArrayList<String>(2);
            derivedTo.set(0, derivedFrom.get(1));
            derivedTo.set(1, derivedFrom.get(0));
            
            if (!derivedTos.contains(derivedTo)) {
                //TODO create the inverses       
                log.info("TODO create "+derivedTo.get(0)+" -> "+derivedTo.get(1));
            }
        }

        for (List<String> derivedTo : derivedTos) {
            List<String> derivedFrom = new ArrayList<String>(2);
            derivedFrom.set(0, derivedTo.get(1));
            derivedFrom.set(1, derivedTo.get(0));
            
            if (derivedFroms.contains(derivedFrom)) {
                //TODO delete the originals  
                log.info("TODO delete "+derivedFrom.get(0)+" <- "+derivedFrom.get(1));
            }
        }
                
        em.close();
    }
    
    private Set<List<String>> getDerivedFrom() {
        
        Query q = em.createQuery("FROM BioSample bs INNER JOIN bs.propertyValues AS pv INNER JOIN pv.type AS pt WHERE pt = 'Derived From'");
        
        List<BioSample> results = q.getResultList();
        Set<List<String>> toReturn = new HashSet<List<String>>();
        for (BioSample bs : results) {
            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : bs.getPropertyValues()) {
                if (pv.getType().getTermText().equals("Derived From")) {
                    List<String> pair = new ArrayList<String>(2);
                    pair.set(0, bs.getAcc());
                    pair.set(1, pv.getTermText());
                    toReturn.add(Collections.unmodifiableList(pair));
                }
            }
        }
        
        return toReturn;
    }
    
    private Set<List<String>> getDerivedTo() {
        
        Query q = em.createQuery("FROM BioSample bs INNER JOIN bs.propertyValues AS pv INNER JOIN pv.type AS pt WHERE pt = 'Derived To'");
        
        List<BioSample> results = q.getResultList();
        Set<List<String>> toReturn = new HashSet<List<String>>();
        for (BioSample bs : results) {
            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : bs.getPropertyValues()) {
                if (pv.getType().getTermText().equals("Derived To")) {
                    List<String> pair = new ArrayList<String>(2);
                    pair.set(0, bs.getAcc());
                    pair.set(1, pv.getTermText());
                    toReturn.add(Collections.unmodifiableList(pair));
                }
            }
        }
        
        return toReturn;
    }
    

    public static void main(String[] args)  {
        new RelationshipInverter().doMain(args);

    }
}
