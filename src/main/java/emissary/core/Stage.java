package emissary.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Information class about processing stages in the workflow
 */
public class Stage {
    private StageEnum stageEnum;

    public enum StageEnum {
        UNDEFINED(-2) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "UNDEFINED";
            }
        },
        STUDY(0) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "STUDY";
            }
        },
        ID(1) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "ID";
            }
        },
        COORDINATE(2) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "COORDINATE";
            }
        },
        PRETRANSFORM(3) {
            @Override
            public boolean isParallelStage() {
                return true;
            }

            @Override
            public String getStageName() {
                return "PRETRANSFORM";
            }
        },
        TRANSFORM(4) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "TRANSFORM";
            }
        },
        POSTTRANSFORM(5) {
            @Override
            public boolean isParallelStage() {
                return true;
            }

            @Override
            public String getStageName() {
                return "POSTTRANSFORM";
            }
        },
        ANALYZE(6) {
            @Override
            public boolean isParallelStage() {
                return true;
            }

            @Override
            public String getStageName() {
                return "ANALYZE";
            }
        },
        VERIFY(7) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "VERIFY";
            }
        },
        IO(8) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "IO";
            }
        },
        REVIEW(9) {
            @Override
            public boolean isParallelStage() {
                return false;
            }

            @Override
            public String getStageName() {
                return "REVIEW";
            }
        };

        private final int index;

        public boolean isParallelStage() {
            return false;
        }

        public abstract String getStageName();

        public int getIndex() {
            return index;
        }

        StageEnum(int index) {
            this.index = index;
        }
    }

    public StageEnum getStageEnum() {
        return stageEnum;
    }

    public void setStageEnum(StageEnum stageEnum) {
        this.stageEnum = stageEnum;
    }

    public boolean isParallelStage() {
        return this.stageEnum.isParallelStage();
    }

    public String getStageName() {
        return this.stageEnum.getStageName();
    }

    public StageEnum getStage(int index) {
        switch (index) {
            case 0:
                return StageEnum.STUDY;
            case 1:
                return StageEnum.ID;
            case 2:
                return StageEnum.COORDINATE;
            case 3:
                return StageEnum.PRETRANSFORM;
            case 4:
                return StageEnum.TRANSFORM;
            case 5:
                return StageEnum.POSTTRANSFORM;
            case 6:
                return StageEnum.ANALYZE;
            case 7:
                return StageEnum.VERIFY;
            case 8:
                return StageEnum.IO;
            case 9:
                return StageEnum.REVIEW;
            default:
                return StageEnum.UNDEFINED;
        }
    }

    public StageEnum getStage(String name) {
        switch (name) {
            case "STUDY":
                return StageEnum.STUDY;
            case "ID":
                return StageEnum.ID;
            case "COORDINATE":
                return StageEnum.COORDINATE;
            case "PRETRANSFORM":
                return StageEnum.PRETRANSFORM;
            case "TRANSFORM":
                return StageEnum.TRANSFORM;
            case "POSTTRANSFORM":
                return StageEnum.POSTTRANSFORM;
            case "ANALYZE":
                return StageEnum.ANALYZE;
            case "VERIFY":
                return StageEnum.VERIFY;
            case "IO":
                return StageEnum.IO;
            case "REVIEW":
                return StageEnum.REVIEW;
            default:
                return StageEnum.UNDEFINED;
        }
    }

    public List<String> getStages() {
        List<String> stages = new ArrayList<>();
        Arrays.stream(StageEnum.values())
                .filter(stageType -> stageType != StageEnum.UNDEFINED)
                .map(StageEnum::getStageName)
                .forEach(stages::add);
        return stages;
    }

    public int getSize() {
        return StageEnum.values().length;
    }

    public String nextStageAfter(String stage) {
        String nextStage = this.getStage(this.getStage(stage).getIndex() + 1).getStageName();
        return nextStage.equals("UNDEFINED") ? null : nextStage;
    }
}
