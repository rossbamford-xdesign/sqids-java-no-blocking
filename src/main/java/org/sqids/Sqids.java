package org.sqids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sqids is designed to generate short YouTube-looking IDs from numbers.
 * <p>
 * This is the Java implementation of https://github.com/sqids/sqids-spec.
 *
 * This implementation is immutable and thread-safe, no lock is necessary.
 */
public class Sqids {
    /**
    * The minimum allowable length of the alphabet used for encoding and
    * decoding Sqids.
    */
    public static final int MIN_ALPHABET_LENGTH = 3;

    /**
     * The maximum allowable minimum length of an encoded Sqid.
     */
    public static final int MIN_LENGTH_LIMIT = 255;

    private final String alphabet;
    private final int alphabetLength;
    private final int minLength;

    private Sqids(final Builder builder) {
        final String alphabet = builder.alphabet;
        final int alphabetLength = alphabet.length();
        final int minLength = builder.minLength;

        if (alphabet.getBytes().length != alphabetLength) {
            throw new IllegalArgumentException("Alphabet cannot contain multibyte characters");
        }

        if (alphabetLength < MIN_ALPHABET_LENGTH) {
            throw new IllegalArgumentException("Alphabet length must be at least " + MIN_ALPHABET_LENGTH);
        }

        if (new HashSet<>(Arrays.asList(alphabet.split(""))).size() != alphabetLength) {
            throw new IllegalArgumentException("Alphabet must contain unique characters");
        }

        if (minLength < 0 || minLength > MIN_LENGTH_LIMIT) {
            throw new IllegalArgumentException("Minimum length has to be between 0 and " + MIN_LENGTH_LIMIT);
        }

        this.alphabet = this.shuffle(alphabet);
        this.alphabetLength = this.alphabet.length();
        this.minLength = minLength;
    }

    /**
     * Generate a Sqids' Builder.
     *
     * @return New Builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Encode a list of numbers to a Sqids ID.
     *
     * @param numbers Numbers to encode.
     * @return Sqids ID.
     */
    public String encode(final List<Long> numbers) {
        if (numbers.isEmpty()) {
            return "";
        }
        for (Long num : numbers) {
            if (num < 0) {
                throw new RuntimeException("Encoding supports numbers between 0 and " + Long.MAX_VALUE);
            }
        }
        return encodeNumbers(numbers);
    }

    /**
     * Decode a Sqids ID back to numbers.
     *
     * @param id ID to decode.
     * @return List of decoded numbers.
     */
    public List<Long> decode(final String id) {
        List<Long> ret = new ArrayList<>();
        if (id.isEmpty()) {
            return ret;
        }

        final char[] alphabetChars = this.alphabet.toCharArray();
        Set<Character> alphabetSet = new HashSet<>();
        for (final char c : alphabetChars) {
            alphabetSet.add(c);
        }
        for (final char c : id.toCharArray()) {
            if (!alphabetSet.contains(c)) {
                return ret;
            }
        }

        final char prefix = id.charAt(0);
        final int offset = this.alphabet.indexOf(prefix);
        String alphabet = new StringBuilder(this.alphabet.substring(offset))
                .append(this.alphabet, 0, offset)
                .reverse()
                .toString();

        int index = 1;
        while (true) {
            final char separator = alphabet.charAt(0);
            int separatorIndex = id.indexOf(separator, index);
            if (separatorIndex == -1) {
                separatorIndex = id.length();
            } else if (index == separatorIndex) {
                break;
            }
            ret.add(toNumber(id, index, separatorIndex, alphabet.substring(1)));
            index = separatorIndex + 1;
            if (index < id.length()) {
                alphabet = shuffle(alphabet);
            } else {
                break;
            }
        }
        return ret;
    }

    private String encodeNumbers(final List<Long> numbers) {
        return this.encodeNumbers(numbers, 0);
    }

    private String encodeNumbers(final List<Long> numbers, final int increment) {
        if (increment > this.alphabetLength) {
            throw new RuntimeException("Reached max attempts to re-generate the ID");
        }

        final int numberSize = numbers.size();
        long offset = numberSize;
        for (int i = 0; i < numberSize; i++) {
            offset = offset + this.alphabet.charAt((int) (numbers.get(i) % this.alphabetLength)) + i;
        }
        offset %= this.alphabetLength;
        offset = (offset + increment) % this.alphabetLength;

        final StringBuilder alphabetB = new StringBuilder(this.alphabet.substring((int) offset))
                .append(this.alphabet, 0, (int) offset);
        final char prefix = alphabetB.charAt(0);
        String alphabet = alphabetB.reverse().toString();
        final StringBuilder id = new StringBuilder().append(prefix);
        for (int i = 0; i < numberSize; i++) {
            final long num = numbers.get(i);
            id.append(toId(num, alphabet.substring(1)));
            if (i < numberSize - 1) {
                id.append(alphabet.charAt(0));
                alphabet = shuffle(alphabet);
            }
        }

        if (this.minLength > id.length()) {
            id.append(alphabet.charAt(0));
            while (this.minLength - id.length() > 0) {
                alphabet = shuffle(alphabet);
                id.append(alphabet, 0, Math.min(this.minLength - id.length(), alphabet.length()));
            }
        }

        return id.toString();
    }

    private String shuffle(final String alphabet) {
        char[] chars = alphabet.toCharArray();
        int charLength = chars.length;
        for (int i = 0, j = charLength - 1; j > 0; i++, j--) {
            int r = (i * j + chars[i] + chars[j]) % charLength;
            char temp = chars[i];
            chars[i] = chars[r];
            chars[r] = temp;
        }

        return new String(chars);
    }

    private StringBuilder toId(long num, final String alphabet) {
        StringBuilder id = new StringBuilder();
        char[] chars = alphabet.toCharArray();
        int charLength = chars.length;

        do {
            id.append(chars[(int) (num % charLength)]);
            num /= charLength;
        } while (num > 0);

        return id.reverse();
    }

    private long toNumber(final String id, final int fromInclusive, final int toExclusive, final String alphabet) {
        int alphabetLength = alphabet.length();
        long number = 0;
        for (int i = fromInclusive; i < toExclusive; i++) {
            char c = id.charAt(i);
            number = number * alphabetLength + alphabet.indexOf(c);
        }
        return number;
    }

    /**
     * Default Sqids' {@code Builder}.
     */
    public static final class Builder {
        /**
         * Default Alphabet used by {@code Builder}.
         */
        public static final String DEFAULT_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        /**
         * Default Minimum length used by {@code Builder}.
         */
        public static final int DEFAULT_MIN_LENGTH = 0;

        private String alphabet = DEFAULT_ALPHABET;
        private int minLength = DEFAULT_MIN_LENGTH;

        /**
         * Set {@code Builder}'s alphabet.
         *
         * @param alphabet The new {@code Builder}'s alphabet
         * @return this {@code Builder} object
         */
        public Builder alphabet(final String alphabet) {
            if (alphabet != null) {
                this.alphabet = alphabet;
            }
            return this;
        }

        /**
         * Set {@code Builder}'s minimum length.
         *
         * @param minLength The new {@code Builder}'s minimum length.
         * @return this {@code Builder} object
         */
        public Builder minLength(final int minLength) {
            this.minLength = minLength;
            return this;
        }

        /**
         * Returns a newly-created {@code Sqids} based on the contents of this {@code Builder}.
         *
         * @return New Sqids instance.
         */
        public Sqids build() {
            return new Sqids(this);
        }
    }
}
