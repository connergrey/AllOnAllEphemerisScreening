import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

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

}
