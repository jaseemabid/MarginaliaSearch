package nu.marginalia.util.dict;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

public class DictionaryData {

    private final int DICTIONARY_BANK_SIZE;
    private static final Logger logger = LoggerFactory.getLogger(DictionaryData.class);

    private final ArrayList<DictionaryDataBank> banks = new ArrayList(100);

    public DictionaryData(int bankSize) {
        DICTIONARY_BANK_SIZE = bankSize;

        banks.add(new DictionaryDataBank(0, bankSize));
    }

    public int add(long key) {
        var activeBank = banks.get(banks.size()-1);
        int rb = activeBank.add(key);

        if (rb == -1) {
            int end = activeBank.getEnd();
            logger.debug("Switching bank @ {}", end);
            var newBank = new DictionaryDataBank(end, DICTIONARY_BANK_SIZE);
            rb = newBank.add(key);

            banks.add(newBank);
        }

        return rb;
    }


    public long getKey(int offset) {
        return banks.get(offset/DICTIONARY_BANK_SIZE).getKey(offset);
    }
    public boolean keyEquals(int offset, long otherKey) {
        return banks.get(offset/DICTIONARY_BANK_SIZE).keyEquals(offset, otherKey);
    }

    private static class DictionaryDataBank {

        private final int start_idx;

        // Humongous long-lived arrays seem to sometimes yield considerable memory overhead and
        // can make the GC behave poorly. Using off-heap memory seems preferred when their
        // lifetime is "forever"

        private final LongBuffer keys;

        private int size;
        private final int capacity;


        public DictionaryDataBank(int start_idx, int sz) {
            this.start_idx = start_idx;
            this.capacity = sz;

            keys = ByteBuffer.allocateDirect(8*capacity).asLongBuffer();
            size = 0;
        }

        public int getStart() {
            return start_idx;
        }

        public int getEnd() {
            return start_idx + size;
        }

        public long getKey(int idx) {
            if (idx < start_idx || idx - start_idx >= size) {
                throw new IndexOutOfBoundsException(idx);
            }
            return keys.get(idx - start_idx);
        }

        public boolean keyEquals(int idx, long other) {
            if (idx < start_idx || idx - start_idx >= size) {
                throw new IndexOutOfBoundsException(idx);
            }

            return keys.get(idx - start_idx) == other;
        }

        public int add(long newKey) {
            if (size >= capacity)
                return -1;

            keys.put(size, newKey);

            return start_idx + size++;
        }

        public int getSize() {
            return size;
        }
    }
}
