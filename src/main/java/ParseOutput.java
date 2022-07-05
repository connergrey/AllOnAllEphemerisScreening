import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class ParseOutput {

    public static void main(String[] arg) throws FileNotFoundException {

        //conjunctions are valid over multiple days, so  create this outside the loop
        Map< List<Integer> , List<Conjunction> > conjunctionMap = new HashMap<>();


        for (String yeardoy : new String[]{ "2022164" }) {

            //local folder to write to
            String local = String.format("screen_%s",yeardoy);

            String Results = String.format("Results_%s",yeardoy);


            // ---------- read all 1-on-1 KCSM files and merge into one
            String fullKCSMpath = String.format("%s/full_kcsm_%s.csv", local, yeardoy);
            PrintWriter fullKCSM = new PrintWriter(new File(fullKCSMpath));


            // --------------------- I MADE CHANGES HERE!!! ---------------------------------------------------------------
            File resultsFolder = new File(String.format("%s/%s", local, Results));
            File[] kcsmFileNames = resultsFolder.listFiles();
            int q = 0;
            for (File kcsmName : kcsmFileNames) {
                if (kcsmName.toString().contains(".DS_Store")) {
                    continue;
                }

                Scanner readKCSM = new Scanner(new File(kcsmName.getAbsolutePath()));

                if (q == 0) {
                    fullKCSM.write(readKCSM.nextLine() + "\n"); //write header
                } else {
                    readKCSM.nextLine(); //skip header
                }

                while (readKCSM.hasNextLine()) {
                    //check if its within the ellipsoid

                    //read the kcsm file
                    KCSM kcsm = new KCSM(readKCSM.nextLine());
                    Vector3D priPos = new Vector3D(kcsm.getX_PRI(), kcsm.getY_PRI(), kcsm.getZ_PRI());
                    Vector3D priVel = new Vector3D(kcsm.getVX_PRI(), kcsm.getVY_PRI(), kcsm.getVZ_PRI());
                    Vector3D secPos = new Vector3D(kcsm.getX_SEC(), kcsm.getY_SEC(), kcsm.getZ_SEC());
                    Vector3D secVel = new Vector3D(kcsm.getVX_SEC(), kcsm.getVY_SEC(), kcsm.getVZ_SEC());

                    //convert miss distance to RIC frame
                    Vector3D relPos = secPos.subtract(priPos); //in XYZ,meters
                    double[] ricMDkm = AllOnAllEphemerisScreening.getRICMD(priPos, priVel, relPos); //in RIC, km

                    //calculate perigee altitude and eccentricity
                    double[] hpANDe = AllOnAllEphemerisScreening.getMinAltAndEcc(priPos, priVel);

                    //determine the ellipsoid size (for primary)
                    double[] ellipSize = AllOnAllEphemerisScreening.getEllipsoidSize(hpANDe);

                    //System.out.println(ricMDkm[0] + " of " + ellipSize[0]);

                    //check if its within the ellipsoid
                    double ellipseEq = FastMath.pow(ricMDkm[0] / ellipSize[0], 2) + FastMath.pow(ricMDkm[1] / ellipSize[1], 2)
                            + FastMath.pow(ricMDkm[2] / ellipSize[2], 2);// if this is less than 1, then the point is within the ellipse
                    boolean isConjunctionInEllipsoid = ellipseEq < 1; //true if it is, false if it is not

                    if (isConjunctionInEllipsoid) { //if the conjunction is within the ellipsoid
                        fullKCSM.write(kcsm.getRawKCSM() + "\n"); //write the line in the new file

                    }

                }
                readKCSM.close();
                q++;
                if(q%1000==0){
                    System.out.println(q);
                }
            }
            fullKCSM.close();

            // ------------ parse the output KCSM ------------------------------

            //String fullKCSMpath = String.format( "%s/full_kcsm_%s.csv",local,yeardoy );
            //String fullKCSMpath = String.format( "%s/many_many_%s.csv",local,yeardoy );
            Scanner scanFullKCSM = new Scanner(new File(fullKCSMpath));
            scanFullKCSM.nextLine(); //skip the header

            //  ------------- identify conjunctions --------------------------
            // start with a list of zero conjunctions, then as CDMs come in we check if they match any conjunctions

            int i = 0;
            while (scanFullKCSM.hasNextLine()) {

                //read the KCSM line
                KCSM newKCSM = new KCSM(scanFullKCSM.nextLine());

                //create a CDM class from the KCSM line
                CDM newCDM = new CDM(newKCSM.getSATPRI(), newKCSM.getSATSEC(), newKCSM.getCONJTIME());

                //find all conjunctions betweeen these sat pairs

                List<Integer> satIDs = new ArrayList<>();
                satIDs.add(newCDM.getSat1());
                satIDs.add(newCDM.getSat2());

                if (conjunctionMap.containsKey(satIDs)) {
                    //there is one or more conjunctions for this sat pair
                    //loop through them and see if our CDM correslates to any of them
                    List<Conjunction> possibleConjunctions = conjunctionMap.get(satIDs);

                    boolean added = false;
                    for (Conjunction conj : possibleConjunctions) {

                        if (conj.getLatestCDM().isInWindow(newCDM)) {
                            //CDM is correlated to one of the conjunctions that already exists
                            conj.addCDM(newCDM);
                            added = true;

                        }
                    }

                    //go to the next kcsm line if the cdm was added to a conj. does not add one to count.
                    if (added) {
                        continue;
                    }

                    //if the CDM does not fall in the window of any of these conjunction, create a new Conjunction
                    Conjunction newConj = new Conjunction(newCDM);
                    conjunctionMap.get(satIDs).add(newConj);

                    //increment the count
                    AllOnAllEphemerisScreening.ConjunctionPairs.Conjunction(satIDs.get(0), satIDs.get(1));

                } else {
                    // no conjunctions for this sat pair, make one and add it to the map
                    List<Conjunction> newConjList = new ArrayList<>();
                    Conjunction newConj = new Conjunction(newCDM);
                    newConjList.add(newConj);
                    conjunctionMap.put(satIDs, newConjList);

                    //increment the count
                    AllOnAllEphemerisScreening.ConjunctionPairs.Conjunction(satIDs.get(0), satIDs.get(1));
                }

            }


            //go to next day
        }



        //load the conjunction pairs reults
        Map<List<Integer>,Integer> conjPairs = AllOnAllEphemerisScreening.ConjunctionPairs.getConjPairs();
        Map< Integer,String > opNames = AllOnAllEphemerisScreening.getOpNameMap();

        //sort the conjunction pairs
        Map<List<Integer>,Integer> sortedConjPairs = AllOnAllEphemerisScreening.sortByValue(conjPairs);

        //create output order for the opIDs to go in the csv
        List<Integer> outputOpOrder = new ArrayList<>();
        for( List<Integer> opIDPair : sortedConjPairs.keySet() ){

            // count the number of conjunctions for each org pair
            //System.out.println(String.format("%s and %s conjuncted %d times",opNames.get(opIDPair.get(0)),opNames.get(opIDPair.get(1)),conjPairs.get(opIDPair)));

            //create order by those involved in the most conjunctions

            for (Integer opID : opIDPair){

                if( !outputOpOrder.contains(opID) ){
                    outputOpOrder.add(opID);
                }

            }

        }


        //write to a csv
        PrintWriter conjOutput = new PrintWriter(new File("allConjunctionsByOperator.csv"));

        //write the first line
        for (Integer opID : outputOpOrder){
            //comma goes before %s because we need to indent the first row to leave room for the first column
            conjOutput.write( String.format( ",%s", opNames.get(opID).replace(",",";") ) );//to avoid csv issues
        }
        conjOutput.write("\n");

        //write all the other lines
        int j = 0;
        for (Integer opID : outputOpOrder){
            //write the op name in the first column
            conjOutput.write( String.format( "%s,", opNames.get(opID).replace(",",";") ) );//to avoid csv issues

            //now write all the conjunctions between this operator and all other operators
            //go from current opID to last op ID
            // less than current opID are "-"

            int k = 0;
            for (Integer opIDInner : outputOpOrder){

                if( k >= j){
                    //we write it
                    List<Integer> tempOpIDs = new ArrayList<>();

                    int lower = FastMath.min(opID,opIDInner);
                    if (lower == opID){
                        tempOpIDs.add( opID );
                        tempOpIDs.add( opIDInner );
                    }else{
                        tempOpIDs.add( opIDInner );
                        tempOpIDs.add( opID );
                    }

                    conjOutput.write( String.format("%d,", conjPairs.getOrDefault( tempOpIDs , 0 )) );
                }else{
                    //we dont write it
                    conjOutput.write(",");
                }

                k++;
            }

            conjOutput.write("\n");
            j++;
        }
        conjOutput.close();




    }


}
