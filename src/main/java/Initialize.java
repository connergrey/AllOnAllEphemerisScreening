import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;

import java.io.*;
import java.util.*;

public class Initialize {
    public String[] loop;
    public Command command;
    public Map<String, List<Integer> > primaries = new HashMap<>();
    public Map<String, List<Integer> > secondaries = new HashMap<>();
    public List<String> queries = new ArrayList<>();

    public Initialize(String[] loop){
        this.command = new Command();
        this.loop = loop;
    }

    public void oneVCMBased(int sat1, int sat2) throws IOException, InterruptedException {

        for (String yeardoy:loop) {

            // --------------- auto created inputs ------------------------

            // sp vector path
            String path = null;
            int year = Integer.parseInt(yeardoy.substring(0, 4));
            //for 2022 files
            if (year == 2022) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy.substring(2) + "/scratch/SP_VEC";
                //for 2021 files
            } else if (year == 2021) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy + "/scratch/SP_VEC";
            } else {
                System.out.println("error");
            }

            //local folder to write to
            String local = String.format("screen_%s", yeardoy);

            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/", localRoot, local);

            //create runtime command
            Command command = new Command();

            File localFol = new File(local);
            if (!localFol.exists()) {
                localFol.mkdir();
            }else{
                System.out.println("This folder already exists, delete it or change the input ID");
            }//create folder if it doesnt exist


            // -----------------------------------------------------------------------------------------------

            // ---------- convert all the SP Ephemeris in input folder to Modified ITC Ephemeris ----------


            //create group.txt
            PrintWriter group = new PrintWriter("group.txt");

            for (int satNo : new int[] {sat1,sat2}) {
                //for each norad id, convert to the format in text file
                String satNoString = Integer.toString(satNo);;
                if (satNoString.length() == 1) {
                    satNoString = "0000" + Integer.toString(satNo);
                } else if (satNoString.length() == 2) {
                    satNoString = "000" + Integer.toString(satNo);
                } else if (satNoString.length() == 3) {
                    satNoString = "00" + Integer.toString(satNo);
                } else if (satNoString.length() == 4) {
                    satNoString = "0" + Integer.toString(satNo);
                }
                group.write(satNoString + "\n");
            }
            group.close();


            // ----------------------------------------------------------------------------------------------

            // ----------------------------------------------------------------------------------------------
            // ------------- create the vcm -----------------
            VCMGenerator generator = new VCMGenerator(path, yeardoy, "group.txt");
            generator.generateVCM(String.format("%s/%s.vcm", local, yeardoy));

            // ----------------------------------------------------------------------------------------------
            // ---- create the query --------------------------
            PrintWriter query = new PrintWriter(String.format("%s/query_%s.txt", local, yeardoy));
            query.write("source /usr/local/KRATOS/env_linux.sh; /usr/local/KRATOS/bin/kratos_ca ");
            //EC2 local VCM path name and file name
            query.write(String.format("--vcm_fname %s%s.vcm ", localFolder, yeardoy));

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance();

            int dayOfYear = Integer.parseInt(yeardoy.substring(4));

            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);

            query.write(String.format("--screen_start %d %d %d 12 00 00 ", calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)));

            query.write(String.format("--csv_fname %smany_many_%s.csv ", localFolder, yeardoy));
            query.write("--miss_thresh 51 ");

            final long endTime = System.currentTimeMillis()/1000;

            // these ones are constants
            query.write("--screen_length 5 ");
            query.write("--csv_type 2 ");
            query.write("--num_threads 2 ");
            query.write(String.format("--log_fname %slog_%s_%d.txt ",localFolder,yeardoy,endTime));
            query.write("--lic_fname /usr/local/KRATOS/license.txt ");
            query.write("--eop_fname /usr/local/KRATOS/data/finals.data.iau1980.txt ");
            query.write("--sw_fname /usr/local/KRATOS/data/sw-celestrak.csv");
            query.write( String.format(" --satnum_pri %d --satnum_sec %d --multi_conj --ref_kcsm", sat1, sat2) );


            query.close();

            // ----------------------------------------------------------------------------------------------


            // ------------- zip up the files -----------------

            System.out.println("Zipping locally.. may take some time");
            //option 1: auto
            command.send(String.format("tar -czvf %s.tar.gz %s",local,local));

            System.out.println("Zipped locally");

            // ----------------------------------------------------------------------------------------------
            // ------------- push files to S3 -----------------
            command.send(
                    String.format("aws s3 cp /Users/connergrey/AllOnAllEphemerisScreening/%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov", local, local)
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


            command.sendEC2(
                    String.format("'rm /mnt/data/grey/logs/out%s.txt'",yeardoy)
            );

            System.out.println("Kratos many-on-many begin");
            command.sendNohupEC2(
                    String.format("'%s'",queryScan.nextLine()) , yeardoy
            );
            System.out.println("Kratos many-on-many end");




        }


    }

    public void startVCMBased(String opt) throws IOException, InterruptedException {

        //create runtime command
        Command command = new Command();

        //op id map
        Map< Integer,Integer > noradOpID = AllOnAllEphemerisScreening.getOpIDMap();

        for (String yeardoy:loop) {

            // --------------- auto created inputs ------------------------

            // sp vector path
            String path = null;
            int year = Integer.parseInt(yeardoy.substring(0, 4));
            //for 2022 files
            if (year == 2022) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy.substring(2) + "/scratch/SP_VEC";
                //for 2021 files
            } else if (year == 2021) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy + "/scratch/SP_VEC";
            } else {
                System.out.println("error");
            }

            //local folder to write to
            String local = String.format("screen_%s", yeardoy);

            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/", localRoot, local);


            // -----------------------------------------------------------------------------------------------

            // ---------- convert all the SP Ephemeris in input folder to Modified ITC Ephemeris ----------


            File localFol = new File(local);
            if (!localFol.exists()) {
                localFol.mkdir();
            }else{
                System.out.println("This folder already exists, delete it or change the input ID");
            }//create folder if it doesnt exist


            //create group.txt
            PrintWriter group = new PrintWriter("group.txt");

            File folderPath = new File(path);
            for (File filePath : folderPath.listFiles()) {

                if (filePath.toString().compareTo(path + "/.DS_Store") == 0) {
                    continue;
                }

                for (File file : filePath.listFiles()) {

                    String noradID = file.getName().substring(0, 5);
                    if(opt.equals("exclude debris")) {

                        int norID = Integer.parseInt(noradID);
                        int orgID = noradOpID.getOrDefault(norID, 0);
                        //find the organization ID related to the Norad ID
                        if (orgID != 0) {
                            group.write(noradID + "\n");
                            System.out.println("writing " + noradID + " on " + yeardoy);
                        }else{
                            System.out.println("skipping " + noradID + " on " + yeardoy);
                        }
                    }else if(opt.equals("with debris")){
                        group.write(noradID + "\n");
                    }else{
                        System.out.println("err");
                    }

                }
            }
            group.close();

            // ----------------------------------------------------------------------------------------------

            // ----------------------------------------------------------------------------------------------
            // ------------- create the vcm -----------------
            VCMGenerator generator = new VCMGenerator(path, yeardoy, "group.txt");
            generator.generateVCM(String.format("%s/%s.vcm", local, yeardoy));

            // ----------------------------------------------------------------------------------------------
            // ---- create the query --------------------------
            PrintWriter query = new PrintWriter(String.format("%s/query_%s.txt", local, yeardoy));
            query.write("source /usr/local/KRATOS/env_linux.sh; /usr/local/KRATOS/bin/kratos_ca ");
            //EC2 local VCM path name and file name
            query.write(String.format("--vcm_fname %s%s.vcm ", localFolder, yeardoy));

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance();

            int dayOfYear = Integer.parseInt(yeardoy.substring(4));

            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);

            query.write(String.format("--screen_start %d %d %d 12 00 00 ", calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)));

            query.write(String.format("--csv_fname %smany_many_%s.csv ", localFolder, yeardoy));
            query.write("--miss_thresh 51 ");

            final long endTime = System.currentTimeMillis()/1000;

            // these ones are constants
            query.write("--screen_length 5 ");
            query.write("--csv_type 2 ");
            query.write("--num_threads 6 ");
            query.write("--prefilter 2 15 ");
            query.write(String.format("--log_fname %slog_%s_%d.txt ",localFolder,yeardoy,endTime));
            query.write("--lic_fname /usr/local/KRATOS/license.txt ");
            query.write("--eop_fname /usr/local/KRATOS/data/finals.data.iau1980.txt ");
            query.write("--sw_fname /usr/local/KRATOS/data/sw-celestrak.csv");

            query.close();

            // ----------------------------------------------------------------------------------------------



            // ------------- zip up the files -----------------

            System.out.println("Zipping locally.. may take some time");
            //option 1: auto
            command.send(String.format("tar -czvf %s.tar.gz %s",local,local));

            //option 2: manual

/*System.out.println("type cd AllOnAllEphemerisScreening");
            System.out.println(String.format("type tar -czvf %s.tar.gz %s", local, local));
            System.out.println("press enter when its complete");
            System.in.read();*/



            System.out.println("Zipped locally");

            // ----------------------------------------------------------------------------------------------
            // ------------- push files to S3 -----------------
            command.send(
                    String.format("aws s3 cp /Users/connergrey/AllOnAllEphemerisScreening/%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov", local, local)
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

            //System.out.println("type in:");
            //String.format("ssh 10.20.128.55 '%s'",queryScan.nextLine());

            //time the all on all screening

            //final long startTime = System.currentTimeMillis();

            command.sendEC2(
                    String.format("'rm /mnt/data/grey/logs/out%s.txt'",yeardoy)
            );

            System.out.println("Kratos many-on-many begin");
            command.sendNohupEC2(
                    String.format("'%s'",queryScan.nextLine()) , yeardoy
            );
            System.out.println("Kratos many-on-many end");

            //final long endTime = System.currentTimeMillis();

            //long dt = (endTime - startTime)/1000;
            //System.out.println("Total execution time: " + dt + " seconds" );



        }

    }

    public void start() throws InterruptedException, IOException {

        for (String yeardoy:loop) {

            // --------------- auto created inputs ------------------------

            //input SP Ephem Folder
            File spEphemFolder = new File(String.format("/Users/connergrey/Documents/SP EPHEMS/SPEphemeris_%s", yeardoy));
            //File spEphemFolder = new File(String.format("/Users/connergrey/Documents/SP EPHEMS/SPEphemTest_%s", yeardoy));

            // sp vector path
            String path = null;
            int year = Integer.parseInt(yeardoy.substring(0, 4));
            //for 2022 files
            if (year == 2022) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy.substring(2) + "/scratch/SP_VEC";
                //for 2021 files
            } else if (year == 2021) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy + "/scratch/SP_VEC";
            } else {
                System.out.println("error");
            }

            //local folder to write to
            String local = String.format("screen_%s", yeardoy);

            //output Mod ITC Ephem Folder.. This is where we want to write the Mod ITC SP Ephems to
            String modITCpathOUT = String.format("/Users/connergrey/AllOnAllEphemerisScreening/%s/ModITCEphems_%s/", local, yeardoy);

            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/", localRoot, local);

            //name of one on one folder
            String OneOnOne = String.format("OneOnOne");
            String Results = String.format("Results_%s", yeardoy);

            ///kcsm file name
            String kcsmFileName = String.format("many_many_%s.csv", yeardoy);

            //create runtime command
            Command command = new Command();

            //op id map
            Map< Integer,Integer > noradOpID = AllOnAllEphemerisScreening.getOpIDMap();

            // -----------------------------------------------------------------------------------------------

            // ---------- convert all the SP Ephemeris in input folder to Modified ITC Ephemeris ----------



            File localFol = new File(local);
            if (!localFol.exists()) {
                localFol.mkdir();
            }else{
                System.out.println("This folder already exists, delete it or change the input ID");
            }//create folder if it doesnt exist

            File outputFolder = new File(modITCpathOUT);
            if (!outputFolder.exists()) {
                outputFolder.mkdir();
            } //create folder if it doesnt exist

            System.out.println("Converting SP Ephem...");
            AllOnAllEphemerisScreening.convertEphemFolder(spEphemFolder,outputFolder,path,noradOpID); //only need to run once per folder, takes a long time
            System.out.println("SP Ephem conversion complete");

/*            //delete the SP Ephem folder that fed this data
            //doesnt work beacause of the space in the name
            command.send(

                    String.format("rm %s", spEphemFolder.getAbsolutePath())
            );*/
            // ----------------------------------------------------------------------------------------------

            // ----------------------------------------------------------------------------------------------
            // ------------- create the vcm -----------------
            VCMGenerator generator = new VCMGenerator(path, yeardoy, "group.txt");
            generator.generateVCM(String.format("%s/%s.vcm", local, yeardoy));

            // ----------------------------------------------------------------------------------------------
            // ---- create the query --------------------------
            PrintWriter query = new PrintWriter(String.format("%s/query_%s.txt", local, yeardoy));
            query.write("source /usr/local/KRATOS/env_linux.sh; /usr/local/KRATOS/bin/kratos_ca ");
            //EC2 local VCM path name and file name
            query.write(String.format("--vcm_fname %s%s.vcm ", localFolder, yeardoy));

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar calendar = Calendar.getInstance();

            int dayOfYear = Integer.parseInt(yeardoy.substring(4));

            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);

            query.write(String.format("--screen_start %d %d %d 12 00 00 ", calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)));

            query.write(String.format("--csv_fname %smany_many_%s.csv ", localFolder, yeardoy));
            //query.write(String.format("--ooe_fname %sooe_%s.csv ", localFolder, yeardoy));
            query.write("--miss_thresh 51 ");

            final long endTime = System.currentTimeMillis()/1000;

            // these ones are constants
            query.write("--screen_length 5 ");
            query.write("--csv_type 2 ");
            query.write("--num_threads 6 ");
            query.write(String.format("--log_fname %slog_%s_%d.txt ",localFolder,yeardoy,endTime));
            query.write("--lic_fname /usr/local/KRATOS/license.txt ");
            query.write("--eop_fname /usr/local/KRATOS/data/finals.data.iau1980.txt ");
            query.write("--sw_fname /usr/local/KRATOS/data/sw-celestrak.csv");

            query.close();

            // ----------------------------------------------------------------------------------------------


            // ------------- zip up the files -----------------

            System.out.println("Zipping locally.. may take some time");
            //option 1: auto
            command.send(String.format("tar -czvf %s.tar.gz %s",local,local));

            //option 2: manual
            /*System.out.println("type cd AllOnAllEphemerisScreening");
            System.out.println(String.format("type tar -czvf %s.tar.gz %s", local, local));
            System.out.println("press enter when its complete");
            System.in.read();*/


            System.out.println("Zipped locally");

            // ----------------------------------------------------------------------------------------------
            // ------------- push files to S3 -----------------
            command.send(
                    String.format("aws s3 cp /Users/connergrey/AllOnAllEphemerisScreening/%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov", local, local)
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

            //System.out.println("type in:");
            //String.format("ssh 10.20.128.55 '%s'",queryScan.nextLine());

            //time the all on all screening

            //final long startTime = System.currentTimeMillis();

            command.sendEC2(
                    String.format("'rm /mnt/data/grey/logs/out%s.txt'",yeardoy)
            );

            System.out.println("Kratos many-on-many begin");
            command.sendNohupEC2(
                    String.format("'%s'",queryScan.nextLine()) , yeardoy
            );
            System.out.println("Kratos many-on-many end");

            //final long endTime = System.currentTimeMillis();

            //long dt = (endTime - startTime)/1000;
            //System.out.println("Total execution time: " + dt + " seconds" );


        }


    }

    public void monitor() throws InterruptedException, IOException {

        boolean[] done = new boolean[loop.length];
        Arrays.fill(done, false); // initialize all processes as not done

        boolean allDone = false;
        while(allDone == false) {//while theyre not all done

            for (int i = 0; i < loop.length; i++) {
                String yeardoy = loop[i];
                //get status updates on each process
                String lastLine = command.sendEC2Last(String.format("'tail -1 /mnt/data/grey/logs/out%s.txt'",yeardoy));
                if(lastLine != null && lastLine.contains("Licensing operations shutdown")){
                    done[i] = true;
                    System.out.println(yeardoy + " is done");
                }else{
                    System.out.println("Current state of " +yeardoy + ": " + lastLine);
                }

            }

            //check if all the processes are done
            for (boolean thisDone : done) {
                if(!thisDone){
                    //if not all are done, wait 5 seconds and check again
                    Thread.sleep(5000);
                    break;
                }
                System.out.println("All done!");
                allDone = true;
            }

        }
    }

    public void prepOneOnOne() throws IOException, InterruptedException {

        PrintWriter allqueries = new PrintWriter(new File("allqueries.txt"));
        for (String yeardoy:loop) {

            //input SP Ephem Folder
            File spEphemFolder = new File(String.format("/Users/connergrey/Documents/SP EPHEMS/SPEphemeris_%s", yeardoy));

            // sp vector path
            String path = null;
            int year = Integer.parseInt(yeardoy.substring(0, 4));
            //for 2022 files
            if (year == 2022) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy.substring(2) + "/scratch/SP_VEC";
                //for 2021 files
            } else if (year == 2021) {
                path = "/Users/connergrey/Documents/SP VECTORS/vectors_" + yeardoy + "/scratch/SP_VEC";
            } else {
                System.out.println("error");
            }

            //local folder to write to
            String local = String.format("screen_%s", yeardoy);

            //output Mod ITC Ephem Folder.. This is where we want to write the Mod ITC SP Ephems to
            String modITCpathOUT = String.format("/Users/connergrey/AllOnAllEphemerisScreening/%s/ModITCEphems_%s/", local, yeardoy);

            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/", localRoot, local);

            //name of one on one folder
            String OneOnOne = String.format("OneOnOne");
            String Results = String.format("Results_%s", yeardoy);

            ///kcsm file name
            String kcsmFileName = String.format("many_many_%s.csv", yeardoy);



            //upload kcsm to S3
            command.sendEC2(
                    String.format("'aws s3 cp %s%s s3://astro-s3-conner/SPEphemerisScreening/%s/%s --profile gov'", localFolder, kcsmFileName, local, kcsmFileName)
            );
            System.out.println("Uploaded KCSM to S3");

            //download kcsm from S3
            command.send(
                    String.format("aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s /Users/connergrey/AllOnAllEphemerisScreening/%s --profile gov", local, kcsmFileName, local)
            );
            System.out.println("Downloaded KCSM to local");


            // ----------------------------------------------------------------------------------------------
            // ------------- filter conjunctions by the ellipsoid screen size -----------------
            //read the many_many_doy.csv KCSM file AND write real conjunctions to a new version
            Scanner scan = new Scanner(new File(local + "/" + kcsmFileName));
            PrintWriter writer = new PrintWriter(new File(String.format("%s/filtered_%s", local, kcsmFileName)));

            writer.write(scan.nextLine() + "\n"); //skip header and write to writer

            //store all conjuncting pairs in lists
            List<Integer> primaries = new ArrayList<>();
            List<Integer> secondaries = new ArrayList<>();

            //on each line:
            while (scan.hasNextLine()) {
                //read the kcsm file
                KCSM kcsm = new KCSM(scan.nextLine());
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
                    writer.write(kcsm.getRawKCSM() + "\n"); //write the line in the new file
                    primaries.add(kcsm.getSATPRI());
                    secondaries.add(kcsm.getSATSEC());
                }

            }
            scan.close();
            writer.close();

            // ----------------------------------------------------------------------------------------------

            //run 1-on-1 screenings iteratively


            int N = primaries.size();

            for (int i = 0; i < N; i++) {
                //-----------------------------read the kcsm file-----------------------------

                int pri = primaries.get(i);
                int sec = secondaries.get(i);

                //-----------------------------create folder for 1-on-1 data-----------------------------
                File OneOnOneFolder = new File(String.format("%s/%s", local, OneOnOne));
                if (!OneOnOneFolder.exists()) {
                    OneOnOneFolder.mkdir();
                } //create folder if it doesnt exist

                //String oneOnOneName = String.format("%d_%d_%s",pri,sec,yeardoy);
                String oneOnOneName = String.format("%d_%d", pri, sec);

                String oneOnOneFileName = String.format("%s/%s/%s", local, OneOnOne, oneOnOneName);
                File oneOnOneFile = new File(oneOnOneFileName);
                if (!oneOnOneFile.exists()) {
                    oneOnOneFile.mkdir();
                } //create folder if it doesnt exist

                //-----------------------------write ooe csv file -----------------------------

                //create csv
                PrintWriter oneOnOneOoe = new PrintWriter(String.format("%s/ooe_%s.csv", oneOnOneFileName, oneOnOneName));
                oneOnOneOoe.write("NORAD_CAT_ID,EPHEM_FNAME\n");

                //also create group.txt
                String oneOnOneGroupPath = String.format("%s/group_%s.txt", oneOnOneFileName, oneOnOneName);
                PrintWriter oneOnOneGroup = new PrintWriter(new File(oneOnOneGroupPath));


                for (int satNum : new int[]{pri, sec}) {

                    String noradIDStr = String.valueOf(satNum);
                    oneOnOneOoe.write(String.format("%s,%sModITCEphems_%s/%d_%s.txt\n", noradIDStr, localFolder, yeardoy, satNum, yeardoy));

                    oneOnOneGroup.write(noradIDStr + "\n");
                }

                oneOnOneOoe.close();
                oneOnOneGroup.close();

                //-----------------------------write vcm-----------------------------

                VCMGenerator oneOnOneGenerator = new VCMGenerator(path, yeardoy, oneOnOneGroupPath);
                String oneOnOneVCMFName = String.format("%s/%s.vcm", oneOnOneFileName, oneOnOneName);
                oneOnOneGenerator.generateVCM(oneOnOneVCMFName);

                //-----------------------------write query-----------------------------
                PrintWriter oneOnOneQuery = new PrintWriter(String.format("%s/query_%s.txt", oneOnOneFileName, oneOnOneName));

                //edit the query for all on all
                Scanner oneOnOneScan = new Scanner(new File(String.format("%s/query_%s.txt", local, yeardoy)));

                String originalQuery = oneOnOneScan.nextLine();

                //replace all the stuff specific to the One on One
                String changedQuery = originalQuery
                        //.replace("source /usr/local/KRATOS/env_linux.sh; ", "")
                        .replace(String.format("%s/", local), String.format("%s/", oneOnOneFileName))
                        .replace(String.format("%s.vcm", yeardoy), String.format("%s.vcm", oneOnOneName))
                        .replace(String.format("many_many_%s.csv", yeardoy), String.format("%s.csv", oneOnOneName))
                        .replace(String.format("%s/%s/%s.csv", OneOnOne, oneOnOneName, oneOnOneName), String.format("%s/%s.csv", Results, oneOnOneName))
                        .replace(String.format("ooe_%s.csv", yeardoy), String.format("ooe_%s.csv", oneOnOneName))
                        .replace("--num_threads 6","--num_threads 2");

                oneOnOneQuery.write(changedQuery);
                allqueries.write(changedQuery);

                //add the extra arguments
                String extraArgs = String.format(" --satnum_pri %d --satnum_sec %d --multi_conj --ref_kcsm", pri, sec);
                oneOnOneQuery.write(extraArgs);
                allqueries.write(extraArgs + "\n");

                oneOnOneQuery.close();
                oneOnOneScan.close();

                // ----------------------------------------------------------------------------------------------
                // ------------- push files to S3 -----------------

            }


            // ----------------------------------------------------------------------------------------------
            // ------------- Zip OneOnOne Folder locally -----------------

            command.send(
                    String.format("tar -czvf %s.tar.gz %s", OneOnOne, OneOnOne), String.format("/Users/connergrey/AllOnAllEphemerisScreening/%s", local)
            );
            System.out.println("One on One folder Zipped locally");

            // ----------------------------------------------------------------------------------------------
            // ------------- push files to S3 -----------------
            command.send(
                    String.format("aws s3 cp /Users/connergrey/AllOnAllEphemerisScreening/%s/%s.tar.gz s3://astro-s3-conner/SPEphemerisScreening/%s/ --profile gov", local, OneOnOne, local)
            );
            System.out.println(String.format("1-on-1 screening folders uploaded to S3"));


            // ----------------------------------------------------------------------------------------------
            // ------------- send command to EC2 to pull One on One file from S3 -----------------

            command.sendEC2(
                    String.format("'aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s.tar.gz %s --profile gov'", local, OneOnOne, localFolder)
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
                    String.format("'mkdir %s%s'", localFolder, Results)
            );


            //save primaries and secondaries for the day
            this.primaries.put(yeardoy,primaries);
            this.secondaries.put(yeardoy,secondaries);
        }
        allqueries.close();

    }

    public boolean[] runInParallel(int P) throws IOException, InterruptedException {

        //read all queries
        Scanner scan = new Scanner(new File("allqueries.txt"));
        //Scanner scan = new Scanner(new File("somequeries.txt"));


        //delete all old log files
        //for each process
        for (int i = 0; i < P; i++) {
            //remove the log file for this process
            command.sendEC2(
                    String.format("'rm /mnt/data/grey/logs/out%s.txt'", String.valueOf(i) )
            );
        }

        boolean[] done = new boolean[P];
        Arrays.fill(done, true); // initialize all processes as done, meaning they are available
        int  j = 0;
        while(scan.hasNextLine()){

            //for each process
            for (int i = 0; i < P; i++) {

                if(done[i]) {//if its done, give it a new task
                    //assign process
                    //get next query
                    String str = "";
                    try {
                        str = scan.nextLine();
                    }catch (Exception e){
                        //next line failed, so we must be at the end of the file
                        break;
                    }
                    //send command to process
                    command.sendNohupEC2(
                            String.format("'%s'",str) , String.valueOf(i)
                    );
                    //mark as not done
                    done[i] = false;

                }else{//if its not done, check the status of its out file
                    //check if last line of out file contains done statement
                    String lastLine = command.sendEC2Last(String.format("'tail -1 /mnt/data/grey/logs/out%s.txt'", String.valueOf(i) ) );
                    if(lastLine != null && lastLine.contains("Licensing operations shutdown")){//if so, mark as done and delete out file

                        //mark as done
                        done[i] = true;
                        //output status
                        System.out.println(++j + " queries complete");
                    }

                }
            }
            //System.out.println("still in send command loop");

        }
        return done;
    }

    public void monitorParallel(boolean[] done) throws IOException, InterruptedException {


        boolean allDone = false;
        while(allDone == false) {//while theyre not all done

            for (int i = 0; i < done.length; i++) {
                String yeardoy = String.valueOf(i); //this is really just a loop ID
                if(!done[i]) {
                    //get status updates on each process
                    String lastLine = command.sendEC2Last(String.format("'tail -1 /mnt/data/grey/logs/out%s.txt'", yeardoy));
                    if (lastLine != null && lastLine.contains("Licensing operations shutdown")) {
                        done[i] = true;
                        System.out.println(yeardoy + " is done");
                    } else {
                        System.out.println("Current state of thread" + yeardoy + ": " + lastLine);
                    }
                }else{
                    System.out.println(yeardoy + " is done");
                }
            }

            //check if all the processes are done
            for (boolean thisDone : done) {
                if(!thisDone){
                    //if not all are done, wait 5 seconds and check again
                    Thread.sleep(5000);
                    break;
                }
                System.out.println("All done!");
                allDone = true;
            }

        }

    }

    public void getResults() throws IOException, InterruptedException {

        for (String yeardoy : loop) {
            //local folder to write to
            String local = String.format("screen_%s", yeardoy);

            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/", localRoot, local);
            String Results = String.format("Results_%s", yeardoy);


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
        }
    }

    public void parseManyMany() throws IOException, InterruptedException {


        //conjunctions are valid over multiple days, so  create this outside the loop
        Map< List<Integer> , List<Conjunction> > conjunctionMap = new HashMap<>();


        for (String yeardoy : loop) {


            //local folder to write to
            String local = String.format("screen_%s",yeardoy);


            //local EC2 folder where the files will be stored
            String localRoot = "/mnt/data/grey/";
            String localFolder = String.format("%s%s/", localRoot, local);

            ///kcsm file name
            String kcsmFileName = String.format("many_many_%s.csv", yeardoy);

            // ----------------------------------------------------------------------------------------------
            // ------------- get output file from EC2 -----------------

/*            //upload kcsm to S3
            command.sendEC2(
                    String.format("'aws s3 cp %s%s s3://astro-s3-conner/SPEphemerisScreening/%s/%s --profile gov'", localFolder,kcsmFileName,local,kcsmFileName)
            );
            System.out.println("Uploaded KCSM to S3");

            //download kcsm from S3
            command.send(
                    String.format("aws s3 cp s3://astro-s3-conner/SPEphemerisScreening/%s/%s /Users/connergrey/AllOnAllEphemerisScreening/%s --profile gov",local,kcsmFileName,local)
            );
            System.out.println("Downloaded KCSM to local");*/


            // ---------- read all 1-on-1 KCSM files and merge into one
            //String fullKCSMpath = String.format("%s/full_kcsm_%s.csv", local, yeardoy);
            // ******* ------ CHANGED DUE TO ALL ON ALL TESTING ****** ----------
            String fullKCSMpath = String.format("%s/many_many_%s.csv", local, yeardoy);


            // ------------ parse the output KCSM ------------------------------

            //String fullKCSMpath = String.format( "%s/full_kcsm_%s.csv",local,yeardoy );
            //String fullKCSMpath = String.format( "%s/many_many_%s.csv",local,yeardoy );
            Scanner scanFullKCSM = new Scanner(new File(fullKCSMpath));

            PrintWriter writer = new PrintWriter(new File(String.format("%s/filtered_many_many_%s.csv", local, yeardoy)));
            writer.write( scanFullKCSM.nextLine() + "\n"); //skip header and write to writer

            //  ------------- identify conjunctions --------------------------
            // start with a list of zero conjunctions, then as CDMs come in we check if they match any conjunctions

            int i = 0;
            int j = 0;
            while (scanFullKCSM.hasNextLine()) {


                //check if its within the ellopsoid

                //read the KCSM line
                KCSM newKCSM = new KCSM(scanFullKCSM.nextLine());

                Vector3D priPos = new Vector3D(newKCSM.getX_PRI(), newKCSM.getY_PRI(), newKCSM.getZ_PRI());
                Vector3D priVel = new Vector3D(newKCSM.getVX_PRI(), newKCSM.getVY_PRI(), newKCSM.getVZ_PRI());
                Vector3D secPos = new Vector3D(newKCSM.getX_SEC(), newKCSM.getY_SEC(), newKCSM.getZ_SEC());
                Vector3D secVel = new Vector3D(newKCSM.getVX_SEC(), newKCSM.getVY_SEC(), newKCSM.getVZ_SEC());

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

                    writer.write( newKCSM.getRawKCSM() + "\n");

                    //create a CDM class from the KCSM line
                    CDM newCDM = new CDM(newKCSM.getSATPRI(), newKCSM.getSATSEC(), newKCSM.getCONJTIME());

                    //takes in a list of norad ids and outputs a list of conjunctions
                    //Map< List<Integer> , List<Conjunction> > conjunctionMap = new HashMap<>();

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
                        i++;

                    } else {
                        // no conjunctions for this sat pair, make one and add it to the map
                        List<Conjunction> newConjList = new ArrayList<>();
                        Conjunction newConj = new Conjunction(newCDM);
                        newConjList.add(newConj);
                        conjunctionMap.put(satIDs, newConjList);

                        //increment the count
                        AllOnAllEphemerisScreening.ConjunctionPairs.Conjunction(satIDs.get(0), satIDs.get(1));
                        j++;
                    }


                }


                //go to next line
            }

            writer.close();
            scanFullKCSM.close();

            System.out.println(i + " and " + j);

            //go to next day
        }

        //load the conjunction pairs reults
        Map<List<Integer>,Integer> conjPairs = AllOnAllEphemerisScreening.ConjunctionPairs.getConjPairs();//pair of operator IDs, and the number of conjunctions between them
        Map< Integer,String > opNames = AllOnAllEphemerisScreening.getOpNameMap();

        //sort the conjunction pairs
        Map<List<Integer>,Integer> sortedConjPairs = AllOnAllEphemerisScreening.sortByValue(conjPairs);

        //create output order for the opIDs to go in the csv
        List<Integer> outputOpOrder = new ArrayList<>();
        for( List<Integer> opIDPair : sortedConjPairs.keySet() ){
            // count the number of conjunctions for each org pair

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
        int j = 1;
        for (Integer opID : outputOpOrder){
            //write the op name in the first column
            conjOutput.write( String.format( "%s,", opNames.get(opID).replace(",",";") ) );//to avoid csv issues

            //now write all the conjunctions between this operator and all other operators
            //go from current opID to last op ID
            // less than current opID are "-"

            int k = 1;
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
