/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2017.                            (c) 2017.
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

package ca.nrc.cadc.caom2.remove;

import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Command line entry point for running the caom2-collection-delete tool.
 *
 * @author jeevesh
 */
public class Main {

    private static Logger log = Logger.getLogger(Main.class);

    private static final Integer DEFAULT_BATCH_SIZE = new Integer(100);
    private static final Integer DEFAULT_BATCH_FACTOR = new Integer(2500);
    private static int exitValue = 0;

    public static void main(String[] args) {
        try {
            ArgumentMap am = new ArgumentMap(args);

            if (am.isSet("d") || am.isSet("debug")) {
                Log4jInit.setLevel("ca.nrc.cadc.caom2.remove", Level.DEBUG);
                Log4jInit.setLevel("ca.nrc.cadc.caom2", Level.DEBUG);
                Log4jInit.setLevel("ca.nrc.cadc.caom2.repo.client", Level.DEBUG);
                Log4jInit.setLevel("ca.nrc.cadc.reg.client", Level.DEBUG);
            } else {
                Log4jInit.setLevel("ca.nrc.cadc", Level.WARN);
                Log4jInit.setLevel("ca.nrc.cadc.caom2.repo.client", Level.WARN);
            }

            if (am.isSet("h") || am.isSet("help")) {
                usage();
                System.exit(0);
            }

            // required args
            String collection = am.getValue("collection");
            boolean nocol = (collection == null || collection.trim().length() == 0);
            if (nocol) {
                log.warn("missing required argument: --collection=<name>");
                usage();
                System.exit(1);
            }

            String database = am.getValue("database");
            boolean nodest = (database == null || database.trim().length() == 0);
            if (nodest) {
                log.warn("missing required argument: --database");
                usage();
                System.exit(1);
            }
            String[] destDS = database.split("[.]");
            if (destDS.length != 3) {
                log.warn("malformed --database value, found " + database + " expected: server.database.schema");
                usage();
                System.exit(1);
            }


            Integer batchSize = null;
            Integer batchFactor = null;
            String sbatch = am.getValue("batchSize");
            String sfactor = am.getValue("batchFactor");

            if (sbatch != null && sbatch.trim().length() > 0) {
                try {
                    batchSize = new Integer(sbatch);
                } catch (NumberFormatException nex) {
                    usage();
                    log.error("value for --batchSize must be an integer, found: " + sbatch);
                    System.exit(1);
                }
            }
            if (sfactor != null && sfactor.trim().length() > 0) {
                try {
                    batchFactor = new Integer(sfactor);
                } catch (NumberFormatException nex) {
                    usage();
                    log.error("value for --batchSize must be an integer, found: " + sbatch);
                    System.exit(1);
                }
            }

            if (batchSize == null) {
                log.debug("no --batchSize specified: defaulting to " + DEFAULT_BATCH_SIZE);
                batchSize = DEFAULT_BATCH_SIZE;
            }
            if (batchFactor == null && batchSize != null) {
                log.debug("no --batchFactor specified: defaulting to " + DEFAULT_BATCH_FACTOR);
                batchFactor = DEFAULT_BATCH_FACTOR;
            }


            Runnable action = null;

            exitValue = 2; // in case we get killed
            Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));

            exitValue = 0; // finished cleanly
        } catch (Throwable t) {
            log.error("uncaught exception", t);
            exitValue = -1;
            System.exit(exitValue);
        } finally {
            System.exit(exitValue);
        }
    }

    private static class ShutdownHook implements Runnable {

        ShutdownHook() {
        }

        @Override
        public void run() {
            if (exitValue != 0) {
                log.error("terminating with exit status " + exitValue);
            }
        }

    }

    private static void usage() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nusage: caom2-remove [-d|--debug] [-h|--help] ...");
        sb.append("\n         --collection=<name> : name of collection to remove (e.g. IRIS)");
        sb.append("\n         --database=<server.database.schema> : collection location");
        sb.append("\n         --source=<server.database.schema> | <resource ID> :  (e.g. ivo://cadc.nrc.ca/caom2repo)" );
        log.warn(sb.toString());
    }
}