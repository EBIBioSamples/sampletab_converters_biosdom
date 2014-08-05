package uk.ac.ebi.fgpt.sampletab.zooma;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;

import uk.ac.ebi.fgpt.zooma.model.AnnotationSummary;
import uk.ac.ebi.fgpt.zooma.model.Property;
import uk.ac.ebi.fgpt.zooma.model.SimpleTypedProperty;
import uk.ac.ebi.fgpt.zooma.search.ZOOMASearchClient;
import uk.ac.ebi.fgpt.zooma.util.ZoomaUtils;

public class ZoomaUtil {

    private float scoreMin = 70.0f;
    private float scoreProp = 0.90f;
    
    private final ZOOMASearchClient zoomaClient;

    private Logger log = LoggerFactory.getLogger(getClass());
    
    
    public ZoomaUtil(ZOOMASearchClient zoomaClient) {
        this.zoomaClient = zoomaClient;
    }

    public ZoomaUtil(URL zoomaUrl) {
        this.zoomaClient = new ZOOMASearchClient(zoomaUrl);
    }
    
    private LoadingCache<List<String>, Optional<AnnotationSummary>> cache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        //.expireAfterAccess(5, TimeUnit.MINUTES)
        .build(
            new CacheLoader<List<String>, Optional<AnnotationSummary>>() {
              public Optional<AnnotationSummary> load(List<String> input) throws Exception {
                //System.out.println("cache miss");
                return Optional.fromNullable(process(input.get(0), input.get(1)));
              }
            });

    
    public AnnotationSummary processCached(String key, String value) throws Exception {
        List<String> inputs = new ArrayList<String>(2);
        inputs.add(key);
        inputs.add(value);
        AnnotationSummary result = cache.get(inputs).orNull();
        return result;
    }
    
    public double cacheHitRate() {
        return cache.stats().hitRate();
    }
    

    public AnnotationSummary process(String key, String value) {
        
        //remove some things that are not worth searching for
        if (!preFilter(key, value)) {
            return null;
        }

        Property property = new SimpleTypedProperty(key, value);
        
        /*
         * Search Zooma only for those annotations that are above the minimum score and within a proportion of the top score
         * 
         */
        //get the raw annotations
        Map<AnnotationSummary, Float> summaries = zoomaClient.searchZOOMA(property, 0);
        if (summaries.size() == 0) {
            return null;
        }
        //filter the annotations to the top proportion (no duplicates)
        Set<AnnotationSummary> annotations = ZoomaUtils.filterAnnotationSummaries(
                summaries, 0, scoreProp);
        //now see what the highest score is
        float topScore = 0.0f;
        for (AnnotationSummary summary : annotations) {
            float score = summaries.get(summary);
            if (score > topScore) {
                topScore = score;
            }
        }
        

        List<AnnotationSummary> annotationList = new ArrayList<AnnotationSummary>();
        annotationList.addAll(annotations);

        
        ComparatorByMap<AnnotationSummary, Float> comparator = new ComparatorByMap<AnnotationSummary, Float>(summaries);
        Collections.sort(annotationList, comparator);
        Collections.reverse(annotationList);
        
        StringBuffer sbuffer = new StringBuffer();
        if (annotations.size() == 0) {
            //no hits
            log.info("[FAIL] Unable to map "+key+" : "+value);
            return null;
        } else if (annotations.size() > 1 && topScore < scoreMin) {
            //fail, too many hits and the top score is too low
            log.info("[FAIL] Unable to map "+key+" : "+value);
            return null;
        } else if (annotations.size() > 1) {
            //requires curation due to multiple hits
            for(AnnotationSummary annotationSummary : annotationList) {
                sbuffer.append(annotationSummary.getAnnotatedPropertyType());
                sbuffer.append(" : ");
                sbuffer.append(annotationSummary.getAnnotatedPropertyValue());
                sbuffer.append(" ("+summaries.get(annotationSummary)+")");
                if (annotationList.get(annotationList.size()-1) != annotationSummary) {
                    sbuffer.append(", ");
                }
            }
            log.info("[CURATE] Multiple hits ("+annotations.size()+") for "+key+" : "+value+" ( "+sbuffer+" )");
            return null;
        } else if (annotations.size() == 1 && topScore < scoreMin) {
            //requires curation due to low score
            AnnotationSummary annotationSummary = annotationList.get(0);
            sbuffer.append(annotationSummary.getAnnotatedPropertyType());
            sbuffer.append(" : ");
            sbuffer.append(annotationSummary.getAnnotatedPropertyValue());
            sbuffer.append(" ("+summaries.get(annotationSummary)+")");
            log.info("[CURATE] Low score hit for "+key+" : "+value+" ( "+sbuffer+" )");
            return null;
        } else if (annotations.size() == 1) {
            //exactly one hit, map to it
            AnnotationSummary annotationSummary = annotationList.get(0);
            sbuffer.append(annotationSummary.getAnnotatedPropertyType());
            sbuffer.append(" : ");
            sbuffer.append(annotationSummary.getAnnotatedPropertyValue());
            sbuffer.append(" ("+summaries.get(annotationSummary)+")");
            log.info("[HIT] Exact hit for "+key+" : "+value+" ( "+sbuffer+" )");
            return annotationSummary;
        }
        
        //should never get here
        return null;
    }
    
    private static class ComparatorByMap<T, U extends Comparable<U>> implements Comparator<T> {

        private final Map<T, U> map;
        
        public ComparatorByMap(Map<T, U> map) {
            this.map = map;
        }
        
        @Override
        public int compare(T o1, T o2) {
            U p1 = map.get(o1);
            U p2 = map.get(o2);
            return p1.compareTo(p2);
        }
        
    }
    
    private boolean preFilter(String key, String value) {
        if (value.length() < 3) {
            return false;
        } else if (value.length() > 100) {
            return false;
        } else if (value.matches("^[^a-zA-Z]*$")) {
            //no letters
            return false;
        } else if (value.matches("^[0-9.-]* [a-zA-Z.]+$")) {
            //probably a number and a unit
            return false;
        } else if (key.toLowerCase().equals("sample name")){ return false;
        } else if (key.toLowerCase().equals("sample description")){ return false;
        } else if (key.toLowerCase().equals("derived from")){ return false;
        } else if (key.toLowerCase().equals("child of")){ return false;
        } else if (key.toLowerCase().equals("synonym")){ return false;

        } else if (key.toLowerCase().equals("label")){ return false;
        } else if (key.toLowerCase().equals("submitted sample id")){ return false;
        } else if (key.toLowerCase().equals("submitted subject id")){ return false;
        } else if (key.toLowerCase().equals("biospecimen repository sample id")){ return false;
        } else if (key.toLowerCase().equals("patient id")){ return false;
        } else if (key.toLowerCase().equals("family id")){ return false;
        } else if (key.toLowerCase().equals("gap subject id")){ return false;
        } else if (key.toLowerCase().equals("gap sample id")){ return false;
        } else if (key.toLowerCase().equals("submitted sample id")){ return false;
        } else if (key.toLowerCase().equals("biospecimen repository sample id")){ return false;
        } else if (key.toLowerCase().equals("anonymized name")){ return false;
        } else if (key.toLowerCase().equals("subject")){ return false;
        } else if (key.toLowerCase().equals("individual")){ return false;
        } else if (key.toLowerCase().equals("sample_source_name")){ return false;
        } else if (key.toLowerCase().equals("secondary description")){ return false;
        } else if (key.toLowerCase().equals("sample_title")){ return false;
        } else if (key.toLowerCase().equals("gene name")){ return false;
        } else if (key.toLowerCase().equals("gene symbol")){ return false;
        } else if (key.toLowerCase().equals("allele name")){ return false;
        } else if (key.toLowerCase().equals("allele symbol")){ return false;
        } else {
            return true;
        }
        
    }

    public float getScoreMin() {
        return scoreMin;
    }

    public void setScoreMin(float scoreMin) {
        this.scoreMin = scoreMin;
    }

    public float getScoreProp() {
        return scoreProp;
    }

    public void setScoreProp(float scoreProp) {
        this.scoreProp = scoreProp;
    }
}
