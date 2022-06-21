import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;
import org.orekit.utils.Constants;

import java.io.*;
import java.util.*;

public class Initialize {

    public static void main(String[] args) throws InterruptedException, IOException {

        for (String yeardoy : new String[]{ "2022167" }) {



            //loads constant data file info
            final File home = new File(System.getProperty("user.home"));
            final File orekitData = new File(home, "orekit-data");
            DataContext.
                    getDefault().
                    getDataProvidersManager().
                    addProvider(new DirectoryCrawler(orekitData));


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

            // -----------------------------------------------------------------------------------------------

            // ---------- convert all the SP Ephemeris in input folder to Modified ITC Ephemeris ----------


            File localFol = new File(local);
            if (!localFol.exists()) {
                localFol.mkdir();
            }else{
                System.out.println("This folder already exists, delete it or change the input ID");
                break;
            }//create folder if it doesnt exist

            File outputFolder = new File(modITCpathOUT);
            if (!outputFolder.exists()) {
                outputFolder.mkdir();
            } //create folder if it doesnt exist

            System.out.println("Converting SP Ephem...");
            AllOnAllEphemerisScreening.convertEphemFolder(spEphemFolder,outputFolder,path); //only need to run once per folder, takes a long time
            System.out.println("SP Ephem conversion complete");

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
            query.write(String.format("--ooe_fname %sooe_%s.csv ", localFolder, yeardoy));
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
            System.out.println(String.format("type tar -czvf %s.tar.gz %s", local, local));
            System.out.println("press enter when its complete");
            System.in.read();

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
            System.out.println("Kratos many-on-many begin");
/*            command.sendEC2(
                    String.format("'%s'",queryScan.nextLine())
            );
            System.out.println("Kratos many-on-many end");*/

            System.out.println("type in:");
            String.format("ssh 10.20.128.55 '%s'",queryScan.nextLine());

        }


    }



}
