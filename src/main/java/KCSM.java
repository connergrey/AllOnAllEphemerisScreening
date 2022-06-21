import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;

import java.math.BigDecimal;

public class KCSM {

    private int SATPRI;
    private int SATSEC;
    private AbsoluteDate CONJTIME;
    private int RELSEP;
    private double X_PRI;
    private double Y_PRI;
    private double Z_PRI;
    private double X_SEC;
    private double Y_SEC;
    private double Z_SEC;
    private double VX_PRI;
    private double VY_PRI;
    private double VZ_PRI;
    private double VX_SEC;
    private double VY_SEC;
    private double VZ_SEC;
    private String rawKCSM;

    final private TimeScale utc = TimeScalesFactory.getUTC();

    public KCSM(String kcsmLine){

        rawKCSM = kcsmLine;

        kcsmLine = kcsmLine.replace("\s",""); //remove spaces
        String[] kcsmCols = kcsmLine.split(","); //split into columns

        SATPRI = Integer.parseInt( kcsmCols[0] );
        SATSEC = Integer.parseInt( kcsmCols[1] );
        CONJTIME = new AbsoluteDate( kcsmCols[2], utc );
        RELSEP = new BigDecimal(kcsmCols[3]).intValue();
        X_PRI = Double.parseDouble( kcsmCols[4] );
        Y_PRI = Double.parseDouble( kcsmCols[5] );
        Z_PRI = Double.parseDouble( kcsmCols[6] );
        X_SEC = Double.parseDouble( kcsmCols[7] );
        Y_SEC = Double.parseDouble( kcsmCols[8] );
        Z_SEC = Double.parseDouble( kcsmCols[9] );
        VX_PRI = Double.parseDouble( kcsmCols[10] );
        VY_PRI = Double.parseDouble( kcsmCols[11] );
        VZ_PRI = Double.parseDouble( kcsmCols[12] );
        VX_SEC = Double.parseDouble( kcsmCols[13] );
        VY_SEC = Double.parseDouble( kcsmCols[14] );
        VZ_SEC = Double.parseDouble( kcsmCols[15] );

    }

    public String getRawKCSM() {
        return rawKCSM;
    }

    public int getSATPRI() {
        return SATPRI;
    }

    public int getSATSEC() {
        return SATSEC;
    }

    public AbsoluteDate getCONJTIME() {
        return CONJTIME;
    }

    public int getRELSEP() {
        return RELSEP;
    }

    public double getX_PRI() {
        return X_PRI;
    }

    public double getY_PRI() {
        return Y_PRI;
    }

    public double getZ_PRI() {
        return Z_PRI;
    }

    public double getVX_PRI() {
        return VX_PRI;
    }

    public double getVY_PRI() {
        return VY_PRI;
    }

    public double getVZ_PRI() {
        return VZ_PRI;
    }

    public double getX_SEC() {
        return X_SEC;
    }

    public double getY_SEC() {
        return Y_SEC;
    }

    public double getZ_SEC() {
        return Z_SEC;
    }

    public double getVX_SEC() {
        return VX_SEC;
    }

    public double getVY_SEC() {
        return VY_SEC;
    }

    public double getVZ_SEC() {
        return VZ_SEC;
    }
}
