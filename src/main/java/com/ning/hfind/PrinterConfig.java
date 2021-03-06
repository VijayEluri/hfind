package com.ning.hfind;

public class PrinterConfig
{
    private boolean depthMode = false;
    private boolean endLineWithNull = false;
    private boolean deleteMode = false;
    private boolean verbose = false;

    public boolean depthMode()
    {
        return depthMode;
    }

    public void setDepthMode(boolean depthMode)
    {
        this.depthMode = depthMode;
    }

    public boolean endLineWithNull()
    {
        return endLineWithNull;
    }

    public void setEndLineWithNull(boolean endLineWithNull)
    {
        this.endLineWithNull = endLineWithNull;
    }

    public boolean deleteMode()
    {
        return deleteMode;
    }

    public void setDeleteMode(boolean deleteMode)
    {
        this.deleteMode = deleteMode;
    }

    public boolean verbose()
    {
        return verbose;
    }

    public void setVerbose(boolean verbose)
    {
        this.verbose = verbose;
    }
}
