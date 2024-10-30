package raft;

import proto.pkg.eraftpb.Eraftpb;
import java.util.List;

public class RaftLog {
    private RaftStorage raftStorage;
    // commit的log index
    private long commited;
    // applied的log index
    private long applied;
    //
    private long stabled;
    // 日志
    List<Eraftpb.Entry> entries;

    private long firstIndex;

    public long Term(long index) {
        if(entries.size() > 0 && index >= firstIndex ) {
            return entries.get((int) (index - firstIndex)).getTerm();
        }
        return 0;
    }

    public long lastIndex() {
        if(entries.size() > 0) {
            return entries.get(entries.size() - 1).getIndex();
        }
        return 0;
    }

    public long firstIndex() {
        return firstIndex;
    }

    public int toEntryIndex(int i) {
        return i;
    }

    public long getCommited() {
        return commited;
    }

    public void setCommited(long commited) {
        this.commited = commited;
    }

    public long getApplied() {
        return applied;
    }

    public void setApplied(long applied) {
        this.applied = applied;
    }

    public long getStabled() {
        return stabled;
    }

    public void setStabled(long stabled) {
        this.stabled = stabled;
    }
}
