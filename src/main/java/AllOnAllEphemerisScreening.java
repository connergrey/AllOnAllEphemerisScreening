import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
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
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

public class AllOnAllEphemerisScreening {

    public static void main(String[] arg) throws IOException, InterruptedException {

        //loads constant data file info
        final File home = new File(System.getProperty("user.home"));
        final File orekitData = new File(home, "orekit-data");
        DataContext.
                getDefault().
                getDataProvidersManager().
                addProvider(new DirectoryCrawler(orekitData));

        // -----------------------------------------------------------------------------------------------
        // --------------- BEFORE RUNNING ------------------------

        //always:
        //aws sso login --profile gov
        //ssh 10.20.128.55 'aws sso login --profile gov'
        //go to the link and type in the code

        //if required:
        //ssh-add .ssh/grey-key.pem

        // --------------- outer loop ------------------------
        //for (String yeardoy : new String[]{ "2021341" }) {
        for (String yeardoy : new String[]{ "2022164" }) {


            // --------------- auto created inputs ------------------------

            //input SP Ephem Folder
            File spEphemFolder = new File(String.format("/Users/connergrey/Documents/SP EPHEMS/SPEphemeris_%s",yeardoy));

            // sp vector path
            String path = null;
            int year = Integer.parseInt( yeardoy.substring(0,4) );
            //for 2022 files
            if(year == 2022) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy.substring(2) + "/scratch/SP_VEC";
                //for 2021 files
            }else if(year == 2021) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy + "/scratch/SP_VEC";
            }else{
                System.out.println("error");
            }

            //local folder to write to
            String local = String.format("screen_%s",yeardoy);

            //output Mod ITC Ephem Folder.. This is where we want to write the Mod ITC SP Ephems to
            String modITCpathOUT = String.format("/Users/connergrey/AllOnAllEphemerisScreening/%s/ModITCEphems_%s/",local,yeardoy);

            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/",localRoot,local);

            //name of one on one folder
            String OneOnOne = String.format("OneOnOne");
            String Results = String.format("Results_%s",yeardoy);

            ///kcsm file name
            String kcsmFileName = String.format("many_many_%s.csv",yeardoy);

            //create runtime command
            Command command = new Command();

            // -----------------------------------------------------------------------------------------------

            // ---------- convert all the SP Ephemeris in input folder to Modified ITC Ephemeris ----------


            File localFol = new File(local);
            if (!localFol.exists()){localFol.mkdir();} //create folder if it doesnt exist

            File outputFolder = new File(modITCpathOUT);
            if (!outputFolder.exists()){outputFolder.mkdir();} //create folder if it doesnt exist

            System.out.println("Converting SP Ephem...");
            convertEphemFolder(spEphemFolder,outputFolder,path); //only need to run once per folder, takes a long time
            System.out.println("SP Ephem conversion complete");

            // ----------------------------------------------------------------------------------------------


            // ----------------------------------------------------------------------------------------------
            // ------------- create the vcm -----------------
            VCMGenerator generator = new VCMGenerator(path,yeardoy,"group.txt");
            generator.generateVCM( String.format("%s/%s.vcm",local,yeardoy) );

            // ----------------------------------------------------------------------------------------------
            // ---- create the query --------------------------
            PrintWriter query = new PrintWriter(String.format("%s/query_%s.txt",local,yeardoy));
            query.write("source /usr/local/KRATOS/env_linux.sh; /usr/local/KRATOS/bin/kratos_ca ");
            //EC2 local VCM path name and file name
            query.write(String.format("--vcm_fname %s%s.vcm ",localFolder,yeardoy));

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance();

            int dayOfYear = Integer.parseInt(yeardoy.substring(4));

            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);

            query.write(String.format("--screen_start %d %d %d 12 00 00 ",calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH)+1,calendar.get(Calendar.DAY_OF_MONTH)));

            query.write(String.format("--csv_fname %smany_many_%s.csv ",localFolder,yeardoy));
            query.write(String.format("--ooe_fname %sooe_%s.csv ",localFolder,yeardoy));
            query.write("--miss_thresh 51 ");

            // these ones are constants
            query.write("--screen_length 5 ");
            query.write("--csv_type 2 ");
            query.write("--ref_kcsm ");
            query.write("--num_threads 32 ");
            query.write("--lic_fname /usr/local/KRATOS/license.txt ");
            query.write("--eop_fname /usr/local/KRATOS/data/finals.data.iau1980.txt ");
            query.write("--sw_fname /usr/local/KRATOS/data/sw-celestrak.csv");

            query.close();

            // ----------------------------------------------------------------------------------------------

            // ------------- zip up the files -----------------

            System.out.println("Zipping locally.. may take some time");
            // ************ DISABLED FOR TESTING ************
            //command.send(String.format("tar -czvf %s.tar.gz %s",local,local));
            // ************ DISABLED FOR TESTING ************
            System.out.println("type cd AllOnAllEphemerisScreening");
            System.out.println(String.format("type tar -czvf %s.tar.gz %s",local,local));
            System.out.println("press enter when its complete");
            System.in.read();

            System.out.println("Zipped locally");

            // ----------------------------------------------------------------------------------------------
            // ------------- push files to S3 -----------------
            command.send(
                    String.format("aws s3 cp /Users/connergrey/AllOnAllEphemerisScreening/%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov",local,local)
            );
            System.out.println("Uploaded to S3");

            // ----------------------------------------------------------------------------------------------
            // ------------- send command to EC2 to pull files from S3 -----------------

            command.sendEC2(
                    String.format("'aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s.tar.gz %s --profile gov'",local,local,localRoot)
            );
            System.out.println("All on All files Download complete on EC2");

            command.sendEC2(
                    String.format("'tar -xvzf %s%s.tar.gz -C %s'", localRoot, local, localRoot)
            );
            System.out.println("Unzipped on EC2");

            command.sendEC2(
                    String.format("'rm %s%s.tar.gz'", localRoot, local, localRoot)
            );
            System.out.println("Deleted zipped file on EC2");


            // ----------------------------------------------------------------------------------------------
            // ------------- send command to EC2 to run the query -----------------
            Scanner queryScan = new Scanner(new File(String.format("%s/query_%s.txt",local,yeardoy)));
            System.out.println("Kratos many-on-many begin");
            command.sendEC2(
                    String.format("'%s'",queryScan.nextLine())
            );
            System.out.println("Kratos many-on-many end");


            // ----------------------------------------------------------------------------------------------
            // ------------- get output file from EC2 -----------------


            //upload kcsm to S3
            command.sendEC2(
                    String.format("'aws s3 cp %s%s s3://astro-s3-conner/SPEphemerisScreening/%s/%s --profile gov'", localFolder,kcsmFileName,local,kcsmFileName)
            );
            System.out.println("Uploaded KCSM to S3");

            //download kcsm from S3
            command.send(
                    String.format("aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s /Users/connergrey/AllOnAllEphemerisScreening/%s --profile gov",local,kcsmFileName,local)
            );
            System.out.println("Downloaded KCSM to local");

            // ----------------------------------------------------------------------------------------------
            // ------------- filter conjunctions by the ellipsoid screen size -----------------
            //read the many_many_doy.csv KCSM file AND write real conjunctions to a new version
            Scanner scan = new Scanner(new File(local + "/"+ kcsmFileName));
            PrintWriter writer = new PrintWriter(new File(String.format("%s/filtered_%s",local,kcsmFileName)));

            writer.write( scan.nextLine() + "\n"); //skip header and write to writer

            //store all conjuncting pairs in lists
            List<Integer> primaries = new ArrayList<>();
            List<Integer> secondaries = new ArrayList<>();

            //on each line:
            while(scan.hasNextLine()) {
                //read the kcsm file
                KCSM kcsm = new KCSM(scan.nextLine());
                Vector3D priPos = new Vector3D(kcsm.getX_PRI(), kcsm.getY_PRI(), kcsm.getZ_PRI());
                Vector3D priVel = new Vector3D(kcsm.getVX_PRI(), kcsm.getVY_PRI(), kcsm.getVZ_PRI());
                Vector3D secPos = new Vector3D(kcsm.getX_SEC(), kcsm.getY_SEC(), kcsm.getZ_SEC());
                Vector3D secVel = new Vector3D(kcsm.getVX_SEC(), kcsm.getVY_SEC(), kcsm.getVZ_SEC());

                //convert miss distance to RIC frame
                Vector3D relPos = secPos.subtract(priPos); //in XYZ,meters
                double[] ricMDkm = getRICMD(priPos, priVel, relPos); //in RIC, km

                //calculate perigee altitude and eccentricity
                double[] hpANDe = getMinAltAndEcc(priPos, priVel);

                //determine the ellipsoid size (for primary)
                double[] ellipSize = getEllipsoidSize(hpANDe);

                //System.out.println(ricMDkm[0] + " of " + ellipSize[0]);

                //check if its within the ellipsoid
                double ellipseEq = FastMath.pow(ricMDkm[0] / ellipSize[0], 2) + FastMath.pow(ricMDkm[1] / ellipSize[1], 2)
                        + FastMath.pow(ricMDkm[2] / ellipSize[2], 2);// if this is less than 1, then the point is within the ellipse
                boolean isConjunctionInEllipsoid = ellipseEq < 1; //true if it is, false if it is not

                if (isConjunctionInEllipsoid) { //if the conjunction is within the ellipsoid
                    writer.write(kcsm.getRawKCSM()+ "\n"); //write the line in the new file
                    primaries.add(kcsm.getSATPRI());
                    secondaries.add(kcsm.getSATSEC());
                }

            }
            scan.close();
            writer.close();

            // ----------------------------------------------------------------------------------------------

            //run 1-on-1 screenings iteratively

            int N = primaries.size();

            for(int i=0;i<N;i++){
                //-----------------------------read the kcsm file-----------------------------
                //KCSM kcsm = new KCSM(scanKCSM.nextLine());

                //int pri = kcsm.getSATPRI();
                //int sec = kcsm.getSATSEC();
                int pri = primaries.get(i);
                int sec = secondaries.get(i);

                //-----------------------------create folder for 1-on-1 data-----------------------------
                File OneOnOneFolder = new File(String.format("%s/%s",local,OneOnOne));
                if (!OneOnOneFolder.exists()){OneOnOneFolder.mkdir();} //create folder if it doesnt exist

                //String oneOnOneName = String.format("%d_%d_%s",pri,sec,yeardoy);
                String oneOnOneName = String.format("%d_%d",pri,sec);

                String oneOnOneFileName = String.format("%s/%s/%s",local,OneOnOne,oneOnOneName);
                File oneOnOneFile = new File(oneOnOneFileName);
                if (!oneOnOneFile.exists()){oneOnOneFile.mkdir();} //create folder if it doesnt exist

                //-----------------------------write ooe csv file -----------------------------

                //create csv
                PrintWriter oneOnOneOoe = new PrintWriter(String.format("%s/ooe_%s.csv",oneOnOneFileName,oneOnOneName));
                oneOnOneOoe.write("NORAD_CAT_ID,EPHEM_FNAME\n");

                //also create group.txt
                String oneOnOneGroupPath = String.format("%s/group_%s.txt",oneOnOneFileName,oneOnOneName);
                PrintWriter oneOnOneGroup = new PrintWriter(new File(oneOnOneGroupPath));


                for (int satNum : new int[]{pri,sec} ){

                    String noradIDStr = String.valueOf(satNum);
                    oneOnOneOoe.write(String.format("%s,%sModITCEphems_%s/%d_%s.txt\n",noradIDStr,localFolder,yeardoy,satNum,yeardoy));

                    oneOnOneGroup.write(noradIDStr + "\n");
                }

                oneOnOneOoe.close();
                oneOnOneGroup.close();

                //-----------------------------write vcm-----------------------------

                VCMGenerator oneOnOneGenerator = new VCMGenerator(path,yeardoy,oneOnOneGroupPath);
                String oneOnOneVCMFName = String.format("%s/%s.vcm",oneOnOneFileName,oneOnOneName);
                oneOnOneGenerator.generateVCM( oneOnOneVCMFName );

                //-----------------------------write query-----------------------------
                PrintWriter oneOnOneQuery = new PrintWriter(String.format("%s/query_%s.txt",oneOnOneFileName,oneOnOneName));

                //edit the query for all on all
                Scanner oneOnOneScan = new Scanner(new File(String.format("%s/query_%s.txt",local,yeardoy)));

                String originalQuery =  oneOnOneScan.nextLine();

                //replace all the stuff specific to the One on One
                String changedQuery = originalQuery
                        .replace("source /usr/local/KRATOS/env_linux.sh; ","")
                        .replace(String.format("%s/",local),String.format("%s/",oneOnOneFileName))
                        .replace(String.format("%s.vcm",yeardoy),String.format("%s.vcm",oneOnOneName))
                        .replace(String.format("many_many_%s.csv",yeardoy),String.format("%s.csv",oneOnOneName))
                        .replace(String.format("%s/%s/%s.csv",OneOnOne,oneOnOneName,oneOnOneName),String.format("%s/%s.csv",Results,oneOnOneName))
                        .replace(String.format("ooe_%s.csv",yeardoy),String.format("ooe_%s.csv",oneOnOneName));

                oneOnOneQuery.write( changedQuery );

                //add the extra arguments
                oneOnOneQuery.write(String.format(" --satnum_pri %d --satnum_sec %d --multi_conj",pri,sec));

                oneOnOneQuery.close();
                oneOnOneScan.close();

                // ----------------------------------------------------------------------------------------------
                // ------------- push files to S3 -----------------


            }

            // ----------------------------------------------------------------------------------------------
            // ------------- Zip OneOnOne Folder locally -----------------

            command.send(
                    String.format("tar -czvf %s.tar.gz %s",OneOnOne,OneOnOne) , String.format("/Users/connergrey/AllOnAllEphemerisScreening/%s",local)
            );
            System.out.println("One on One folder Zipped locally");

            // ----------------------------------------------------------------------------------------------
            // ------------- push files to S3 -----------------
            command.send(
                    String.format("aws s3 cp /Users/connergrey/AllOnAllEphemerisScreening/%s/%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov",local,OneOnOne,local)
            );
            System.out.println(String.format("1-on-1 screening folders uploaded to S3"));


            // ----------------------------------------------------------------------------------------------
            // ------------- send command to EC2 to pull One on One file from S3 -----------------

            command.sendEC2(
                    String.format("'aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s.tar.gz %s --profile gov'",local,OneOnOne,localFolder)
            );
            System.out.println("One on One file Download complete on EC2");


            // -------- send command to EC2 to unzip the one on one folder ----------
            command.sendEC2(
                    String.format("'tar -xvzf %s%s.tar.gz -C %s'", localFolder, OneOnOne, localFolder)
            );
            System.out.println("Unzipped one on one folder on EC2");


            // -------- send command to EC2 to remove the one on one zipped folder ----------
            command.sendEC2(
                    String.format("'rm %s%s.tar.gz'", localFolder, OneOnOne)
            );
            System.out.println("Deleted zipped file on EC2");

            //make the results directory
            command.sendEC2(
                    String.format("'mkdir %s%s'",localFolder,Results)
            );

            StringBuffer buf = new StringBuffer();
            buf.append("'source /usr/local/KRATOS/env_linux.sh; ");
            // ------------- send command to EC2 to run ALL the 1-on-1 queries -----------------
            System.out.println("Kratos 1-on-1 begin");
            for(int i=0;i<N;i++) {

                int pri = primaries.get(i);
                int sec = secondaries.get(i);

                //String oneOnOneName = String.format("%d_%d_%s",pri,sec,yeardoy);
                String oneOnOneName = String.format("%d_%d",pri,sec);

                Scanner queryScanAgain = new Scanner(new File(String.format("%s/%s/%s/query_%s.txt", local,OneOnOne, oneOnOneName,oneOnOneName)));

                buf.append(queryScanAgain.nextLine() + ";\s");

                if (buf.length() > 7000){ //max query length is 8191

                    //run command
                    buf.append("'");
                    command.sendEC2(
                            String.format(buf.toString())
                    );

                    //reinitialize buffer
                    buf.delete(0,buf.length());
                    buf.append("'source /usr/local/KRATOS/env_linux.sh; ");
                }
            }
            if(buf.length() > 0){

                buf.append("'");
                command.sendEC2(
                        String.format(buf.toString())
                );
            }


            System.out.println("Kratos 1-on-1 end");
            // ----------------------------------------------------------------------------------------------
            // ------------- get output file from EC2 -----------------

            //zip up results folder on EC2
            command.sendEC2(
                    //cd to the local file, then zip up the results folder
                    String.format("'cd %s; tar -czvf %s.tar.gz %s'",localFolder,Results,Results)
            );
            System.out.println("Zipped 1-on-1 KCSMs on S3");

            //upload results to S3 on EC2
            command.sendEC2(
                    String.format("'aws s3 cp %s%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov'", localFolder,Results,local)
            );
            System.out.println("Uploaded 1-on-1 KCSMs to S3");

            //download results from s3 to local
            command.send(
                    String.format("aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s.tar.gz /Users/connergrey/AllOnAllEphemerisScreening/%s/ --profile gov",local,Results,local)
            );
            System.out.println("Downloaded 1-on-1 KCSMs from S3 to local");

            //unzip results folder on local
            command.send(
                    String.format("tar -xvzf %s.tar.gz",Results,Results) , String.format("/Users/connergrey/AllOnAllEphemerisScreening/%s",local)
            );
            System.out.println("Unzipped 1-on-1 KCSMs on local");


            // ---------------- parse the output --------------------------------------



        }
        // ---------------- end of outer for loop ------------------------------------------------

        // ----------------- post processing the data -----------------------------------

/*
        // organization id to name key map
        Map< Integer,String > opNames = getOpNameMap();

        for (Integer key : opNames.keySet()){
            System.out.println( key + " maps to " + opNames.get(key));
        }
*/

    }


    public static double[] getRICMD(Vector3D pos,Vector3D vel,Vector3D rRel){

        // define screen's RIC frame (same as RTN)
        Vector3D rHat = pos.normalize();
        Vector3D hVec = pos.crossProduct(vel);
        Vector3D cHat = hVec.normalize();
        Vector3D iHat = cHat.crossProduct(rHat);

        // project relative position onto RIC (RTN)
        double rMD = rRel.dotProduct(rHat)/1000;//in km
        double tMD = rRel.dotProduct(iHat)/1000;
        double nMD = rRel.dotProduct(cHat)/1000;

        return new double[]{rMD,tMD,nMD};

    }

    public static double[] getEllipsoidSize(double[] hpANDe){
        double hp = hpANDe[0];
        double e = hpANDe[1];

        double R = 0;
        double I = 0;
        double C = 0;

        double Rmin = 0.4; //NEEDS TO BE 0.4 IN PRODUCTION

        if(hp <= 500 && e < 0.25 ){
            R = Rmin;
            I = 44;
            C = 51;
        }else if(hp <= 750 && e < 0.25 ){
            R = Rmin;
            I = 25;
            C = 25;
        }else if(hp <= 1200 && e < 0.25 ){
            R = Rmin;
            I = 12;
            C = 12;
        }else if(hp <= 2000 && e < 0.25 ){
            R = Rmin;
            I = 2;
            C = 2;
        }else {
            R = 10;
            I = 10;
            C = 10;
        }

        return new double[]{R,I,C};

    }

    public static double[] getMinAltAndEcc(Vector3D pos, Vector3D vel){ //in m and m/s

        // calculate some orbital elements to find the min and max altitude of the orbit
        double mu = Constants.EGM96_EARTH_MU; //in m^3/s^2 (i think)
        double eRad = Constants.EGM96_EARTH_EQUATORIAL_RADIUS;
        double r = pos.getNorm();
        double v = vel.getNorm();

        double denom = v * v / mu - 2 / r;
        double a = -1 / denom;

        Vector3D hVec = pos.crossProduct(vel);
        Vector3D eVec = (vel.crossProduct(hVec).scalarMultiply(1 / mu)).subtract(pos.normalize());
        double e = eVec.getNorm();

        double altp = (a * (1 - e) - eRad) / 1000;

        return new double[]{altp,e};

    }

    public static void convertEphemFolder(File spEphemFolder, File outputFolder, String spVec) throws FileNotFoundException {

        //extract year-doy
        String[] splt = spEphemFolder.toString().split("/");
        String yeardoy = splt[splt.length-1].substring(12); // yearDOY

        //create ooe and groups writers
        //write ooe and groups.txt
        String local = String.format( "screen_%s",yeardoy );
        String localRoot = "/mnt/data/grey/";
        String localFolder = String.format("%s%s/", localRoot, local);

        //create csv
        PrintWriter ooe = new PrintWriter(String.format("%s/ooe_%s.csv",local,yeardoy));
        ooe.write("NORAD_CAT_ID,EPHEM_FNAME\n");

        //also create group.txt
        PrintWriter group = new PrintWriter("group.txt");


        //loop through to read the file tree
        File[] noradIDFileGroups = spEphemFolder.listFiles();//ouputs 00,01,....52
        for (File noradIDGroup : noradIDFileGroups) {
            if(noradIDGroup.toString().contains(".DS_Store")){continue;}

            File[] spEphemFiles = noradIDGroup.listFiles();//outputs 00005.txt,...,52039.txt
            for (File spEphemFile : spEphemFiles) {
                if(spEphemFile.toString().contains(".DS_Store")){continue;}

                //write modITCephem
                writeModITCEphem(spEphemFile, yeardoy, outputFolder, spVec, ooe, group, localFolder);

            }

        }
        //close ooe and group files
        ooe.close();
        group.close();

    }

    public static void writeModITCEphem(File spEphemFile, String yeardoy, File outputFolder, String spVecPath, PrintWriter ooe, PrintWriter group, String localFolder) throws FileNotFoundException {

        TimeScale utc = TimeScalesFactory.getUTC();

        //write to modifiedITCEphemWriter
        String noradID = spEphemFile.getName().substring(0,5);

        // check if we have the sp vec for this file
        File file = new File( spVecPath + "/" + noradID.substring(0,2) + "/" + noradID + ".txt" );
        if(file.exists()) { //if we dont have VCM, dont include in screening

            String modITCFileName = noradID + "_" + yeardoy + ".txt";


            //open file -> convert to modified itc
            List<TimeStampedPVCoordinates> tspvListEMEJ2000 = SP2EMEList(spEphemFile);

            //check if the ephem file spans the whole time
            if( tspvListEMEJ2000.get( tspvListEMEJ2000.size()-1 ).getDate().durationFrom( tspvListEMEJ2000.get( 0 ).getDate() ) >= 5*86400 ) {
                //if we dont have an entire 5 day ephemeris, dont include in screening
                // ^ a better version of this would check the start and end date, not just the span
                // make sure screen start date and end date are bounded by ephem start and end date


                //create file name
                PrintWriter modifiedITCEphemWriter = new PrintWriter(new File(outputFolder.getAbsolutePath() + "/" + modITCFileName));

                //write modified ITC Ephem
                //write header lines
                modifiedITCEphemWriter.write("Header 1\n");
                modifiedITCEphemWriter.write("Header 2\n");
                modifiedITCEphemWriter.write("Header 3\n");
                modifiedITCEphemWriter.write("UVW\n");

                //write the rest of the file
                StringBuffer text = new StringBuffer();
                TimeZone.setDefault(TimeZone.getTimeZone("UTC")); //need to set default time zone... casue its weird
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyDDDHHmmss.SSS", Locale.US);

                int i = 0;
                for (TimeStampedPVCoordinates tspvEME : tspvListEMEJ2000) {

                    //checks that the new line is timestamped after the previous line
                    //solves the error of having two lines with the same time
                    if( i == 0 || tspvListEMEJ2000.get(i).getDate().isAfter( tspvListEMEJ2000.get(i-1).getDate() )) {

                        //System.out.println(tspvEME.getDate().toString());
                        //System.out.println(dateFormat.format(tspvEME.getDate().toDate(utc)));
                        //write position and velocity data
                        text.append(dateFormat.format(tspvEME.getDate().toDate(utc))).append(" ");
                        text.append(String.format("%.10f ", tspvEME.getPosition().getX()));
                        text.append(String.format("%.10f ", tspvEME.getPosition().getY()));
                        text.append(String.format("%.10f ", tspvEME.getPosition().getZ()));
                        text.append(String.format("%.10f ", tspvEME.getVelocity().getX()));
                        text.append(String.format("%.10f ", tspvEME.getVelocity().getY()));
                        text.append(String.format("%.10f\n", tspvEME.getVelocity().getZ()));

                        //write covariance
                        text.append("0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00\n");
                        text.append("0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00\n");
                        text.append("0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00 0.0000000000e+00\n");

                        //write the string and delete
                        modifiedITCEphemWriter.write(text.toString());
                        text.delete(0, text.length());
                    }
                    i++;
                }

                System.out.println(String.format( "%s created" , modITCFileName));
                modifiedITCEphemWriter.close();

                //write to ooe and groups.txt
                ooe.write(String.format("%s,%sModITCEphems_%s/%s\n",noradID,localFolder,yeardoy,modITCFileName));
                group.write(noradID + "\n");

            }else{
                System.out.println("Ephemeris does not span the entire window for " + noradID);
            }

        }else{
            System.out.println("VCM does not exist for " + noradID);
        }



    }

    public static List<TimeStampedPVCoordinates> SP2EMEList(File spEphemFile) throws FileNotFoundException {

        Frame teme = FramesFactory.getTEME();
        Frame eme2000 = FramesFactory.getEME2000();
        Scanner scan = new Scanner(spEphemFile);

        //initialize arraylist
        List<TimeStampedPVCoordinates> ephemerisPV = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        TimeScale utc = TimeScalesFactory.getUTC();

        //skip line 1
        scan.nextLine();
        while (scan.hasNextLine()) { //loop through all remaining lines

            //Read line 2
            double deltaTime = scan.nextDouble();
            double posX = scan.nextDouble();
            double posY = scan.nextDouble();
            double posZ = scan.nextDouble();
            scan.nextLine();

            //Read line 3
            double dateTime = scan.nextDouble();
            double velX = scan.nextDouble();
            double velY = scan.nextDouble();
            double velZ = scan.nextDouble();
            scan.nextLine();

            //construct a pv vector and time vector
            String pars = String.format("%14.3f",dateTime);

            int year = 2000 + Integer.parseInt(pars.substring(0, 2));

            int dayOfYear = Integer.parseInt(pars.substring(2, 5));
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
            int month = calendar.get(Calendar.MONTH) + 1; // returns 0 - 11
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            int hour = Integer.parseInt(pars.substring(5, 7));
            int min = Integer.parseInt(pars.substring(7, 9));
            double sec = Double.parseDouble(pars.substring(9));

            AbsoluteDate date = new AbsoluteDate(year,month,day,hour,min,sec,utc);

            Vector3D pos = new Vector3D(posX, posY, posZ);
            Vector3D vel = new Vector3D(velX, velY, velZ);
            TimeStampedPVCoordinates pvt = new TimeStampedPVCoordinates(date, pos, vel);

            //transform from TEME to EMEJ2000
            Transform temeToFrame = teme.getTransformTo(eme2000,date);

            //add timestamped position vector to list
            ephemerisPV.add(temeToFrame.transformPVCoordinates(pvt));
        }

        scan.close();
        return ephemerisPV;

    }


}
