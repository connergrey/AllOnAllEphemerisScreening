import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class OneOnOneScreening {

    public static void main(String[] arg) throws IOException, InterruptedException {

        //loads constant data file info
        final File home = new File(System.getProperty("user.home"));
        final File orekitData = new File(home, "orekit-data");
        DataContext.
                getDefault().
                getDataProvidersManager().
                addProvider(new DirectoryCrawler(orekitData));


        // --------------- outer loop ------------------------
        //for (String yeardoy : new String[]{ "2021341" }) {
        for (String yeardoy : new String[]{ "2022164" }) {


            // --------------- auto created inputs ------------------------


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

            //create runtime command
            Command command = new Command();


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
                        .replace("source /usr/local/KRATOS/env_linux.sh; ", "")
                        .replace(String.format("%s/", local), String.format("%s/", oneOnOneFileName))
                        .replace(String.format("%s.vcm", yeardoy), String.format("%s.vcm", oneOnOneName))
                        .replace(String.format("many_many_%s.csv", yeardoy), String.format("%s.csv", oneOnOneName))
                        .replace(String.format("%s/%s/%s.csv", OneOnOne, oneOnOneName, oneOnOneName), String.format("%s/%s.csv", Results, oneOnOneName))
                        .replace(String.format("ooe_%s.csv", yeardoy), String.format("ooe_%s.csv", oneOnOneName))
                        .replace("--num_threads 6","--num_threads 2");

                oneOnOneQuery.write(changedQuery);

                //add the extra arguments
                oneOnOneQuery.write(String.format(" --satnum_pri %d --satnum_sec %d --multi_conj --ref_kcsm", pri, sec));

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









            StringBuffer buf = new StringBuffer();
            buf.append("'source /usr/local/KRATOS/env_linux.sh; ");
            // ------------- send command to EC2 to run ALL the 1-on-1 queries -----------------
            System.out.println("Kratos 1-on-1 begin");
            for (int i = 0; i < N; i++) {

                int pri = primaries.get(i);
                int sec = secondaries.get(i);

                //String oneOnOneName = String.format("%d_%d_%s",pri,sec,yeardoy);
                String oneOnOneName = String.format("%d_%d", pri, sec);

                Scanner queryScanAgain = new Scanner(new File(String.format("%s/%s/%s/query_%s.txt", local, OneOnOne, oneOnOneName, oneOnOneName)));

                buf.append(queryScanAgain.nextLine() + ";\s");

                if (buf.length() > 7000) { //max query length is 8191

                    //run command
                    buf.append("'");
                    command.sendEC2(
                            String.format(buf.toString())
                    );

                    //reinitialize buffer
                    buf.delete(0, buf.length());
                    buf.append("'source /usr/local/KRATOS/env_linux.sh; ");
                }
            }
            if (buf.length() > 0) {

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





        }

    }


}
