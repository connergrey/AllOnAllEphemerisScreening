import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;

public class Command {

    void send(String cmd) throws IOException, InterruptedException {
        System.out.println("Command: "+cmd);
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(cmd,null,new File("/Users/connergrey/AllOnAllEphemerisScreening"));
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));

        String line = "";
        while ((line=buf.readLine())!=null) {
            System.out.println(line);
        }

        String s = "";
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }

        pr.waitFor();

    }

    void send(String cmd, String location) throws IOException, InterruptedException {
        System.out.println("Command: "+cmd);
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(cmd,null,new File(location));
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));

        String line = "";
        while ((line=buf.readLine())!=null) {
            System.out.println(line);
        }

        String s = "";
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }

        pr.waitFor();

    }

    void sendEC2(String cmd) throws IOException, InterruptedException {

        System.out.println("EC2 Command: ssh 10.20.128.55 "+cmd);
        String[] call = ("ssh 10.20.128.55 " + cmd.replace("\s","' '").replace(";'","'\s;") ).split("\s");

        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(call,null,new File("/Users/connergrey/"));
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));

        String line = "";
        while ((line=buf.readLine())!=null) {
            System.out.println(line);
        }

        String s = "";
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }

        pr.waitFor();
    }

    String sendEC2Last(String cmd) throws IOException, InterruptedException {

        //System.out.println("EC2 Command: ssh 10.20.128.55 "+cmd);
        String[] call = ("ssh 10.20.128.55 " + cmd.replace("\s","' '").replace(";'","'\s;") ).split("\s");

        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(call,null,new File("/Users/connergrey/"));
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));

        String line = "";
        while ((line=buf.readLine())!=null) {
            return line;
        }

        pr.waitFor();
        return line;
    }

    String[] sendEC2Print(String cmd) throws IOException, InterruptedException {

        //System.out.println("EC2 Command: ssh 10.20.128.55 "+cmd);
        String[] call = ("ssh 10.20.128.55 " + cmd.replace("\s","' '").replace(";'","'\s;") ).split("\s");

        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(call,null,new File("/Users/connergrey/"));
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));

        String[] lines = new String[3];
        String line = "";
        while ((line=buf.readLine())!=null) {
            Arrays.fill(lines,line);
            if(!lines[2].equals(null)){
                return lines;
            }
        }

        pr.waitFor();
        return lines;
    }

    void sendNohupEC2(String cmd, String outputID) throws IOException, InterruptedException {

        System.out.println("EC2 Command: nohup ssh 10.20.128.55 " + cmd);

        String[] callpt1 = ("nohup ssh 10.20.128.55 " + cmd.replace("\s","' '").replace(";'","'\s;") )
                .split("\s");
        String[] callpt2 = String.format("> /mnt/data/grey/logs/out%s.txt 2> /mnt/data/grey/logs/err%s.txt &",outputID,outputID)
                .split("\s");

        String[] call = new String[callpt1.length + callpt2.length];

        for (int i = 0; i < callpt1.length; i++) {
            call[i] = callpt1[i];
        }

        for (int i = 0; i < callpt2.length; i++) {
            call[i + callpt1.length] = callpt2[i];
        }

/*        for (String inCall: call){
            System.out.println(inCall);
        }*/

       Runtime run = Runtime.getRuntime();
        Process pr = run.exec(call,null,new File("/Users/connergrey/"));
      /*  BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));

        String line = "";
        while ((line=buf.readLine())!=null) {
            System.out.println(line);
        }

        String s = "";
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }*/

    }

}
