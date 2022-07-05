import java.io.IOException;

public class CleanHouse {

    public static void main(String[] arg) throws IOException, InterruptedException {

        Command cmd = new Command();

        //String yeardoy = "2021341";
        //String[] yeardoys = {"2022056","2021341"};
        String[] yeardoys = { "2022164","2022165","2022166","2022167","2022168" };
        for (String yeardoy :
                yeardoys) {


        // delete local folder
        cmd.send(
                String.format("rm -r screen_%s",yeardoy) , String.format("/Users/connergrey/AllOnAllEphemerisScreening")
        );

        //delete S3 folder
        cmd.send(
                String.format("aws s3 rm s3://astro-s3-conner/SPEphemerisScreening/screen_%s --recursive --profile gov",yeardoy)
        );

        //delete EC2 folder
        cmd.sendEC2(
                String.format("'rm -r /mnt/data/grey/screen_%s'",yeardoy)
        );

        }

    }
}
