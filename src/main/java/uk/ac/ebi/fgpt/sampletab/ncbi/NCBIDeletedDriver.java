package uk.ac.ebi.fgpt.sampletab.ncbi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.xml.parsers.ParserConfigurationException;

import org.dom4j.Element;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import uk.ac.ebi.fg.biosd.model.expgraph.BioSample;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fgpt.sampletab.AbstractDriver;
import uk.ac.ebi.fgpt.sampletab.utils.XMLFragmenter;
import uk.ac.ebi.fgpt.sampletab.utils.XMLFragmenter.ElementCallback;

public class NCBIDeletedDriver extends AbstractDriver{

	@Option(name = "--download", aliases = { "-d" }, usage = "downloadfile")
	protected File downloadFile = null;

	private XMLFragmenter fragment;
	
	private List<String> ncbiAccessions = new ArrayList<String>();
	
    private Logger log = LoggerFactory.getLogger(getClass());

	private ElementCallback callback = new ElementCallback() {
		
		@Override
		public void handleElement(Element element) {

			String accession = element.attributeValue("accession");
			ncbiAccessions.add(accession);
		}

		@Override
		public boolean isBlockStart(String uri, String localName,
				String qName, Attributes attributes) {
			//its not a biosample element, skip
			if (!qName.equals("BioSample")) {
				return false;
			}
			//its not public, skip
			if (!attributes.getValue("", "access").equals("public")) {
				return false;
			}
			//its an EBI biosample, or has no accession, skip
			if (attributes.getValue("", "accession") == null || attributes.getValue("", "accession").startsWith("SAME")) {
				return false;
			}
			
			return true;
		}
	};

	public NCBIDeletedDriver() {
		// TODO Auto-generated constructor stub
	}
	
	public void handleGZStream(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
		handleStream(new GZIPInputStream(inputStream));
	}
	
	public void handleStream(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
		fragment.handleStream(inputStream, "UTF-8", callback);	
	}

    @Override
    public void doMain(String[] args){
        super.doMain(args);
        //create an XML fragment handler
		try {
			fragment = XMLFragmenter.newInstance();
		} catch (ParserConfigurationException e) {
			log.error("Unable to create SAX parser", e);
			return;
		} catch (SAXException e) {
			log.error("Unable to create SAX parser", e);
			return;
		}

		//establish a connection to the FTP site
		NCBIFTP ncbiftp = new NCBIFTP();
		ncbiftp.setup("ftp.ncbi.nlm.nih.gov");

		//this is the remote filename we want
		String remoteFile = "/biosample/biosample_set.xml.gz";

		InputStream is = null;
		try {
			if (downloadFile == null) {
				is = ncbiftp.streamFromFTP(remoteFile);
			} else {
				is = ncbiftp.streamFromLocalCopy(remoteFile, downloadFile);
			}
			handleGZStream(is);
		} catch (IOException e) {
			log.error("Error accessing ftp.ncbi.nlm.nih.gov/biosample/biosample_set.xml.gz", e);
			return;
		} catch (ParseException e) {
			log.error("Unable to read date", e);
			return;
		} catch (ParserConfigurationException e) {
			log.error("Unable to create SAX parser", e);
			return;
		} catch (SAXException e) {
			log.error("Unable to handle SAX", e);
			return;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// do nothing
				}
			}
		}
		
		//at this point, there is a list of public NCBI accessions
		
		//check each copy of an NCBI accession in EBI and see if it is public
		EntityManagerFactory emf = Resources.getInstance().getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();

        EntityTransaction transaction = em.getTransaction();
        transaction.begin();

        AccessibleDAO<BioSample> biosampleDAO = new AccessibleDAO<BioSample>(BioSample.class, em); 
        
        String hql = "SELECT bs1.acc FROM BioSample bs1 WHERE substr(bs1.acc,0,4) != 'SAME'";
        Query q = em.createQuery(hql);
        for (Object r : q.getResultList()) {
        	String accession = r.toString();
        	//if its not a public ncbi accession
        	if (!ncbiAccessions.contains(accession)) {
            	log.info("Checking "+accession);
            	
            	BioSample bioSample = biosampleDAO.find(accession);
            	
            	boolean isPublic = false;
            	//check if it was a valid object
            	if (bioSample != null) {
    	        	if ((bioSample.getPublicFlag() != null && bioSample.getPublicFlag() == true ) ) {
    	        		//the sample itself is public
    	        		isPublic = true;
    	        	} else if (bioSample.getReleaseDate() != null && bioSample.getReleaseDate().before(new Date())) {
    	        		//the samples release date is in the past
    	        		isPublic = true;
    	        	} else {
    	        		//the submission may be public, even if the sample is not set to public
    	        		//have to handle the legacy of multiple owner MSIs
    	        		for (MSI msi : bioSample.getMSIs()) {
    	        			if (msi.getPublicFlag() != null && msi.getPublicFlag() == true) {
    	        				//the MSI itself is flagged as public
    	        				isPublic = true;
    	        			} else if (msi.getReleaseDate() != null && msi.getReleaseDate().before(new Date())) {
    	        				//the MSI has a release date in the past
    	        				isPublic = true;	        				
    	        			}
    	        		}
    	        	}
    	        	
    	        	if (isPublic) {
    		        	//this is a sample that is public, from a source that says it should be private
    		        	//so we make it private
    		        	log.info("Discovered that "+accession+" needs to be made private");
    		        	bioSample.setPublicFlag(false);
    		
    		        	//persist it back into the database
    		            biosampleDAO.mergeBean(bioSample);
    		            
    		        	log.info("Finished making "+accession+" private");
    	        	}
            	}        	
        	}
        }
        log.info("Commiting transaction");
        transaction.commit();
        log.info("Commited transaction");
        log.info("Closing entity manager");
        em.close();
        log.info("Closed entity manager");
        log.info("Closing entity manager factory");
        emf.close();
        log.info("Closed entity manager factory");
    }

	public static void main(String[] args) {
		new NCBIDeletedDriver().doMain(args);
	}
}
