package uk.ac.ebi.fgpt.sampletab;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.kohsuke.args4j.Argument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.sampletab.subs.JobRegistryDriver;

public class HQLDriver extends AbstractDriver {

    @Argument(required=true, index=0, metaVar="HQL", usage = "statement to run")
    protected String hql;

    
    private static Logger log =  LoggerFactory.getLogger(JobRegistryDriver.class); 

    @Override
    public void doMain(String[] args) {
        super.doMain(args);

        log.info("executing query: "+hql);

        EntityManager em = Resources.getInstance().getEntityManagerFactory().createEntityManager();

        Query q = em.createQuery( hql );
        
        for (Object r : q.getResultList()) {
            System.out.println(r);
        }
        
    }
}
