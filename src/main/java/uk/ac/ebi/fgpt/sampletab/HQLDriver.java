package uk.ac.ebi.fgpt.sampletab;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.sampletab.subs.JobRegistryDriver;

public class HQLDriver extends AbstractDriver {

    @Argument(required=true, index=0, metaVar="HQL", usage = "statement to run")
    protected String hql;

    @Option(name = "--max", aliases = { "-m" }, usage = "maximum number of results")
    protected int maxCount = 100;

    
    private static Logger log =  LoggerFactory.getLogger(JobRegistryDriver.class); 

    @Override
    public void doMain(String[] args) {
        super.doMain(args);

        log.info("executing query: "+hql);

        EntityManager em = Resources.getInstance().getEntityManagerFactory().createEntityManager();

        for (EntityType<?> et : em.getMetamodel().getEntities()) {
            log.info("Entity name: "+et.getName());
            for (Attribute<?,?> a : et.getAttributes()) {
                log.info("  Attribute name: "+a.getName());
            }
        }
        
        Query q = em.createQuery(hql);
        q.setMaxResults(maxCount);
        
        for (Object r : q.getResultList()) {
            System.out.println(r);
        }
    }
    
    
    public static void main(String[] args)  {
        new HQLDriver().doMain(args);

    }
}
