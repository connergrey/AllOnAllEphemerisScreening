import org.orekit.time.AbsoluteDate;

import java.util.ArrayList;
import java.util.List;

public class Conjunction {

    private List<CDM> cdms;

    public Conjunction(CDM cdm){
        cdms = new ArrayList<>();
        cdms.add(cdm);
    }

    public void addCDM(CDM cdm){
        cdms.add(cdm);
    }

    public CDM getLatestCDM() {
        return cdms.get(cdms.size()-1);
    }

    public int howManyCDMs(){
        return cdms.size();
    }
}
