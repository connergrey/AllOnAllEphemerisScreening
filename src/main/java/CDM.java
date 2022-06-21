import org.hipparchus.util.FastMath;
import org.orekit.time.AbsoluteDate;

public class CDM {

    private int sat1; // maybe even make this private Satellite sat1; where satellite has norad id and org info
    private int sat2;
    private AbsoluteDate tca;

    public CDM(int sat1, int sat2, AbsoluteDate tca){
        //sets the lower number to sat 1, higher number to sat 2
        this.sat1 = FastMath.min(sat1,sat2);
        if(this.sat1 == sat1){
            this.sat2 = sat2;
        }else{
            this.sat2 = sat1;
        }
        this.tca = tca;
    }

    public int getSat1() {
        return sat1;
    }

    public int getSat2() {
        return sat2;
    }

    public AbsoluteDate getTca() {
        return tca;
    }

    public double getWindow(){
        //calculate the minimum orbital period of the two objects
        // window = 0.06*period in seconds
        return 0.06 * 90 * 60;
    }

    public boolean isInWindow(CDM newCDM){
        return newCDM.getTca().isBetween( this.tca.shiftedBy( -1*getWindow()) , this.tca.shiftedBy( getWindow()) );
    }

}