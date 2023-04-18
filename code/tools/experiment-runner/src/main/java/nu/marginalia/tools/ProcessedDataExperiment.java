package nu.marginalia.tools;


import nu.marginalia.converting.instruction.Instruction;

import java.util.List;

public interface ProcessedDataExperiment extends Experiment{

    /** The experiment processes the domain here.
     *
     * @return true to continue, false to terminate.
     */
    boolean process(List<Instruction> instructions);

    /** Invoked after all domains are processed
     *
     */
    void onFinish();

    default boolean isInterested(String domainName) {
        return true;
    }
}
