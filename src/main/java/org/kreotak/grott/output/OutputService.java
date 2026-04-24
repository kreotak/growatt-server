package org.kreotak.grott.output;

import org.kreotak.grott.parser.InverterData;

public interface OutputService {
    void publish(InverterData data);
}
