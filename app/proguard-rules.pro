# kotlinx.serialization keeps what it needs through its own consumer rules.
# Saved games and statistics are plain @Serializable classes; nothing else is
# reached by reflection.
