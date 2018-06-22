/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2018.                            (c) 2018.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 5 $
*
************************************************************************
*/


package ca.nrc.cadc.caom2.artifactsync;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.apache.log4j.Logger;

import ca.nrc.cadc.caom2.Artifact;
import ca.nrc.cadc.caom2.Observation;
import ca.nrc.cadc.caom2.Plane;
import ca.nrc.cadc.caom2.ReleaseType;
import ca.nrc.cadc.caom2.artifact.ArtifactMetadata;
import ca.nrc.cadc.caom2.artifact.ArtifactStore;
import ca.nrc.cadc.caom2.harvester.state.HarvestSkipURI;
import ca.nrc.cadc.caom2.harvester.state.HarvestSkipURIDAO;
import ca.nrc.cadc.caom2.persistence.ObservationDAO;

/**
 * Class that compares artifacts in the caom2 metadata with the artifacts
 * in storage (via ArtifactStore).
 * 
 * @author majorb
 *
 */
public class DbBasedValidator extends ArtifactValidator {
    
    public static final String STATE_CLASS = Artifact.class.getSimpleName();
    
    private ObservationDAO observationDAO;
    private HarvestSkipURIDAO harvestSkipURIDAO;
    private String source;
        
    private static final Logger log = Logger.getLogger(DbBasedValidator.class);
    
    public DbBasedValidator(DataSource dataSource, String[] dbInfo, ObservationDAO observationDAO, 
    		String collection, boolean summaryMode, boolean reportOnly, ArtifactStore artifactStore) {
    	super(collection, summaryMode, reportOnly, artifactStore);
        this.observationDAO = observationDAO;
        this.source = dbInfo[0] + "." + dbInfo[1] + "." + dbInfo[2];
        this.harvestSkipURIDAO = new HarvestSkipURIDAO(dataSource, dbInfo[1], dbInfo[2]);
    }

    protected boolean supportSkipURITable() {
    	return true;
    }
    
    protected boolean checkAddToSkipTable(ArtifactMetadata artifact) throws URISyntaxException {
        // add to HavestSkipURI table if there is not already a row in the table
        Date releaseDate = artifact.releaseDate;
        URI artifactURI = new URI(artifact.artifactURI);
        HarvestSkipURI skip = harvestSkipURIDAO.get(source, STATE_CLASS, artifactURI);
        if (skip == null && releaseDate != null) {
            if (!this.reportOnly) {
                skip = new HarvestSkipURI(source, STATE_CLASS, artifactURI, releaseDate);
                harvestSkipURIDAO.put(skip);
            }
            return true;
        }
        return false;
    }
    
    protected TreeSet<ArtifactMetadata> getLogicalMetadata() throws Exception {
        long start = System.currentTimeMillis();
        List<Observation> observations = observationDAO.getList(Observation.class, null, null, 3);
        TreeSet<ArtifactMetadata> result = getMetadata(observations);
        log.debug("Finished logical query in " + (System.currentTimeMillis() - start) + " ms");
        return result;
    }
    
    private TreeSet<ArtifactMetadata> getMetadata(List<Observation> observations) throws Exception {
        TreeSet<ArtifactMetadata> artifacts = new TreeSet<>(ArtifactMetadata.getComparator());;
        for (Observation obs : observations) {
        	for (Plane plane : obs.getPlanes()) {
        		for (Artifact artifact : plane.getArtifacts()) {
        			ArtifactMetadata metadata = new ArtifactMetadata(); 
        			metadata.artifactURI = artifact.getURI().toASCIIString();
        			metadata.checksum = getStorageChecksum(artifact.contentChecksum.toASCIIString());
        			metadata.contentLength = Long.toString(artifact.contentLength);
        			metadata.contentType = artifact.contentType;
        			metadata.collection = this.collection;
        			metadata.lastModified = artifact.getLastModified();
        			metadata.storageID = this.artifactStore.toStorageID(artifact.getURI().toASCIIString());
        			ReleaseType type = artifact.getReleaseType();
        			if (ReleaseType.DATA.equals(type)) {
        				metadata.releaseDate = plane.dataRelease;
        			} else if (ReleaseType.META.equals(type)) {
        				metadata.releaseDate = plane.metaRelease;
        			} else {
        				metadata.releaseDate = null;
        			}
        			artifacts.add(metadata);
        		}
        	}
        }
        
        return artifacts;
    }
    
    private String getStorageChecksum(String checksum) throws Exception {
        int colon = checksum.indexOf(":");
        return checksum.substring(colon + 1, checksum.length());
    }
}
