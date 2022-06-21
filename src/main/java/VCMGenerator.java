import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;

public class VCMGenerator {

    private String path;
    private Frame eme2000 = FramesFactory.getEME2000();
    private Frame teme = FramesFactory.getTEME();
    private Frame itrf = FramesFactory.getITRF(IERSConventions.IERS_2010,true);
    private TimeScale utc = TimeScalesFactory.getUTC();
    private String yeardoy;
    private String groupPath;

    public VCMGenerator(String path,String yeardoy,String groupPath){

        this.path = path;
        this.yeardoy = yeardoy;
        this.groupPath = groupPath;
    }


    void generateVCM(String name) throws FileNotFoundException {

        // create calendar instance for current data
        //TimeZone timeZone = TimeZone.getTimeZone("UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Calendar cal  = Calendar.getInstance();
        SimpleDateFormat creationDate = new SimpleDateFormat("yyyy DDD (dd MMM) HH:mm:ss.SSS", Locale.US);

        //write to vcm file
        PrintWriter vcm = new PrintWriter(name);

        //read norad IDs from group.txt
        Scanner input = new Scanner(new File(groupPath));

        while(input.hasNext()){
            int satNo = input.nextInt();


            //for each norad id, convert to the format in text file
            String satNoString;
            if(Integer.toString(satNo).length()==1){
                satNoString = "0000"+Integer.toString(satNo);
            }else if(Integer.toString(satNo).length()==2){
                satNoString = "000"+Integer.toString(satNo);
            }else if(Integer.toString(satNo).length()==3){
                satNoString = "00"+Integer.toString(satNo);
            }else if(Integer.toString(satNo).length()==4){
                satNoString = "0"+Integer.toString(satNo);
            }else{
                satNoString = Integer.toString(satNo);
            }

            //pull the sp vector
            String filePath = path + "/" + satNoString.substring(0,2) + "/" + satNoString + ".txt";

            Scanner spvec = new Scanner(new File(filePath));

            //format into vcm
            vcm.write("<> SP VECTOR/COVARIANCE MESSAGE\n");
            vcm.write("<>\n");
            vcm.write("<> MESSAGE TIME (UTC): " + creationDate.format(cal.getTime()).toUpperCase() + "  CENTER:\n");
            vcm.write("<> " + spvec.nextLine() + "\n");//sat no, int des
            vcm.write("<> COMMON NAME:\n");

            //Need to read the date
            spvec.next();
            spvec.next();
            spvec.next();
            int year = spvec.nextInt();
            int dayOfYear = spvec.nextInt();
            spvec.next();
            spvec.next();
            String time = spvec.next();
            int hour = Integer.parseInt(time.substring(0,2));
            int min = Integer.parseInt(time.substring(3,5));
            double sec = 0;
            if(time.length() == 6) {
                sec = spvec.nextDouble();
            }else if(time.length() == 12){
                sec = Double.parseDouble(time.substring(6,time.length()));
            }else{
                System.out.println("theres still an error");
            }
            spvec.next();
            spvec.next();
            int revs = spvec.nextInt();

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, min);
            calendar.set(Calendar.SECOND, (int) sec);
            double num = (sec - (int) sec)*1000;
            calendar.set(Calendar.MILLISECOND, (int) FastMath.round(num));
            int month = calendar.get(Calendar.MONTH) + 1; // returns 0 - 11
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            AbsoluteDate date = new AbsoluteDate(year,month,day,hour,min,sec,utc);
            vcm.write("<> EPOCH TIME (UTC): " + creationDate.format(calendar.getTime()).toUpperCase() + "  EPOCH REV: " + revs + "\n");//epoch time, epoch rev

            //READ POSITION
            spvec.next();
            spvec.next();
            spvec.next();
            double temePosX = spvec.nextDouble();
            double temePosY = spvec.nextDouble();
            double temePosZ = spvec.nextDouble();

            //READ VELOCITY
            spvec.next();
            spvec.next();
            spvec.next();
            double temeVelX = spvec.nextDouble();
            double temeVelY = spvec.nextDouble();
            double temeVelZ = spvec.nextDouble();

            // CONVERT TO PROPER FRAMES
            Vector3D pos = new Vector3D(temePosX,temePosY,temePosZ);
            Vector3D vel = new Vector3D(temeVelX,temeVelY,temeVelZ);
            PVCoordinates pv = new PVCoordinates(pos,vel);

            Transform temeToJ2000 = teme.getTransformTo(eme2000, date);
            PVCoordinates pvJ2K = temeToJ2000.transformPVCoordinates(pv);

            Transform temeToITRF = teme.getTransformTo(itrf, date);
            PVCoordinates pvITRF = temeToITRF.transformPVCoordinates(pv);

            vcm.write("<> J2K POS (KM):" + makeSpaces(pvJ2K.getPosition().getX(),pvJ2K.getPosition().getY(),
                    pvJ2K.getPosition().getZ(),"pos") + "\n");
            vcm.write("<> J2K VEL (KM/S):" + makeSpaces(pvJ2K.getVelocity().getX(),pvJ2K.getVelocity().getY(),
                    pvJ2K.getVelocity().getZ(),"vel") + "\n");
            vcm.write("<> ECI POS (KM):" + makeSpaces(pv.getPosition().getX(),pv.getPosition().getY(),
                    pv.getPosition().getZ(),"pos") + "\n");
            vcm.write("<> ECI VEL (KM/S):" + makeSpaces(pv.getVelocity().getX(),pv.getVelocity().getY(),
                    pv.getVelocity().getZ(),"vel") + "\n");
            vcm.write("<> EFG POS (KM):" + makeSpaces(pvITRF.getPosition().getX(),pvITRF.getPosition().getY(),
                    pvITRF.getPosition().getZ(),"pos") + "\n");
            vcm.write("<> EFG VEL (KM/S):" + makeSpaces(pvITRF.getVelocity().getX(),pvITRF.getVelocity().getY(),
                    pvITRF.getVelocity().getZ(),"vel") + "\n");

            spvec.nextLine();
            vcm.write("<> " + spvec.nextLine() + "\n");//geopotential
            vcm.write("<> " + spvec.nextLine() + "\n");//models
            vcm.write("<> " + spvec.nextLine() + "\n");//ballistic
            vcm.write("<> " + spvec.nextLine() + "\n");//srp
            vcm.write("<> " + spvec.nextLine() + "\n");//thrust
            vcm.write("<> " + spvec.nextLine() + "\n");//weather
            vcm.write("<> " + spvec.nextLine() + "\n");//tai utc
            vcm.write("<> " + spvec.nextLine() + "\n");//polar mot
            vcm.write("<> " + spvec.nextLine() + "\n");//leap sec
            vcm.write("<> " + spvec.nextLine() + "\n");//integrator
            vcm.write("<> " + spvec.nextLine() + "\n");//settings
            vcm.write("<> " + spvec.nextLine() + "\n");//step size

            // THIS IS NOT REAL DATA YET
            vcm.write("<> VECTOR U,V,W SIGMAS (KM):           0.0000     0.0000     0.0000\n");
            vcm.write("<> VECTOR UD,VD,WD SIGMAS (KM/S):      0.0000     0.0000     0.0000\n");
            vcm.write("<> COVARIANCE MATRIX (EQUINOCTIAL ELS): ( 9x 9) WTD RMS:  1.00000E+00\n");

            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");
            vcm.write("<>  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00  0.00000E+00\n");

            //close the sp vec
            spvec.close();

        } // end while loop

        //close the vcm file when all sp vec info is written
        vcm.close();
    }

    private String makeSpaces(double x, double y, double z, String type) {
        if (type.compareTo("pos")==0) {
            int lenx = 3 + 1 + 6 - String.valueOf((int) FastMath.floor(x)).length();
            int leny = 1 + 1 + 6 - String.valueOf((int) FastMath.floor(y)).length();
            int lenz = 1 + 1 + 6 - String.valueOf((int) FastMath.floor(z)).length();
            String spacex = new String(new char[lenx]).replace('\0', ' ');
            String spacey = new String(new char[leny]).replace('\0', ' ');
            String spacez = new String(new char[lenz]).replace('\0', ' ');
            return spacex + String.format("%.8f", x) + spacey + String.format("%.8f", y)
                    + spacez + String.format("%.8f", z);
        } else if (type.compareTo("vel")==0){
            int lenx = 1 + 1 + 2 - String.valueOf((int) FastMath.floor(x)).length();
            int leny = 1 + 1 + 2 - String.valueOf((int) FastMath.floor(y)).length();
            int lenz = 1 + 1 + 2 - String.valueOf((int) FastMath.floor(z)).length();
            String spacex = new String(new char[lenx]).replace('\0', ' ');
            String spacey = new String(new char[leny]).replace('\0', ' ');
            String spacez = new String(new char[lenz]).replace('\0', ' ');
            return spacex + String.format("%.12f", x) + spacey + String.format("%.12f", y)
                    + spacez + String.format("%.12f", z);
        } else {
            return null;
        }
    }

    void findDroppedVCMs(double days) throws FileNotFoundException {

        // only turn this on if you actually filter out VCMs in the 1st method

        //TimeScale utc = TimeScalesFactory.getUTC();

        /*Scanner quick = new Scanner(new File(filePath));
            // GET SAT NO
            quick.next();
            quick.next();
            int satNoTest = quick.nextInt();
            quick.nextLine();

            quick.next();
            quick.next();
            quick.next();
            int year = quick.nextInt();
            int DOY = quick.nextInt();

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR,year);
            cal.set(Calendar.DAY_OF_YEAR,DOY);
            AbsoluteDate spdate = new AbsoluteDate(year,cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH), utc);
            if(spdate.durationFrom(new AbsoluteDate(2022, 04, 16, utc)) / 86400 < -1*days){

                //write satNos that need to be removed from screening

                //System.out.println(satNoTest);
                continue;
            }

            quick.close();*/

    }

}
