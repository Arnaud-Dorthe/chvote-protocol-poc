/*-------------------------------------------------------------------------------------------------
 - #%L                                                                                            -
 - chvote-protocol-poc                                                                            -
 - %%                                                                                             -
 - Copyright (C) 2016 - 2017 République et Canton de Genève                                       -
 - %%                                                                                             -
 - This program is free software: you can redistribute it and/or modify                           -
 - it under the terms of the GNU Affero General Public License as published by                    -
 - the Free Software Foundation, either version 3 of the License, or                              -
 - (at your option) any later version.                                                            -
 -                                                                                                -
 - This program is distributed in the hope that it will be useful,                                -
 - but WITHOUT ANY WARRANTY; without even the implied warranty of                                 -
 - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the                                   -
 - GNU General Public License for more details.                                                   -
 -                                                                                                -
 - You should have received a copy of the GNU Affero General Public License                       -
 - along with this program. If not, see <http://www.gnu.org/licenses/>.                           -
 - #L%                                                                                            -
 -------------------------------------------------------------------------------------------------*/

package ch.ge.ve.protopoc.service.algorithm;

import ch.ge.ve.protopoc.service.model.*;
import ch.ge.ve.protopoc.service.support.RandomGenerator;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ch.ge.ve.protopoc.arithmetic.BigIntegerArithmetic.modExp;
import static ch.ge.ve.protopoc.service.support.BigIntegers.multiplyMod;
import static java.math.BigInteger.ONE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Algorithms related to the decryption of ballots
 */
public class DecryptionAuthorityAlgorithms {
    private static final Logger log = LoggerFactory.getLogger(DecryptionAuthorityAlgorithms.class);
    private final PublicParameters publicParameters;
    private final GeneralAlgorithms generalAlgorithms;
    private final RandomGenerator randomGenerator;

    public DecryptionAuthorityAlgorithms(PublicParameters publicParameters, GeneralAlgorithms generalAlgorithms,
                                         RandomGenerator randomGenerator) {
        this.publicParameters = publicParameters;
        this.generalAlgorithms = generalAlgorithms;
        this.randomGenerator = randomGenerator;
    }

    /**
     * Algorithms 7.47: checkShuffleProofs
     *
     * @param bold_pi   the shuffle proofs generated by the authorities
     * @param e_0       the original encryption
     * @param bold_E    the vector of the re-encryption lists, per authority
     * @param publicKey the public key
     * @param j         the index of this authority
     * @return true if all the proofs generated by the other authorities are valid, false otherwise
     */
    public boolean checkShuffleProofs(List<ShuffleProof> bold_pi, List<Encryption> e_0,
                                      List<List<Encryption>> bold_E, EncryptionPublicKey publicKey, int j) {
        int s = publicParameters.getS();
        int N = e_0.size();
        Preconditions.checkArgument(bold_pi.size() == s,
                "there should be as many proofs as there are authorities");
        Preconditions.checkArgument(bold_E.size() == s,
                "there should be as many lists of re-encryptions as there are authorities");
        Preconditions.checkArgument(bold_E.stream().map(List::size).allMatch(l -> l == N),
                "every re-encryption list should have length N");
        Preconditions.checkElementIndex(j, s,
                "The index of the authority should be valid with respect to the number of authorities");

        // insert e_0 at index 0, thus offsetting all indices for bold_E by 1
        List<List<Encryption>> tmp_bold_e = new ArrayList<>();
        tmp_bold_e.add(0, e_0);
        tmp_bold_e.addAll(bold_E);
        for (int i = 0; i < s; i++) {
            if (i != j) {
                if (!checkShuffleProof(
                        bold_pi.get(i), tmp_bold_e.get(i), tmp_bold_e.get(i + 1), publicKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Algorithm 7.48: CheckShuffleProof
     *
     * @param pi           the proof of validity of the shuffle
     * @param bold_e       the vector of ElGamal encryptions
     * @param bold_e_prime the vector of permuted re-encryptions
     * @param publicKey    the system's public key
     * @return true if and only if the proof is valid for this shuffle
     */
    public boolean checkShuffleProof(ShuffleProof pi, List<Encryption> bold_e, List<Encryption> bold_e_prime,
                                     EncryptionPublicKey publicKey) {
        BigInteger pk = publicKey.getPublicKey();
        BigInteger p = publicParameters.getEncryptionGroup().getP();
        BigInteger q = publicParameters.getEncryptionGroup().getQ();
        BigInteger g = publicParameters.getEncryptionGroup().getG();
        BigInteger h = publicParameters.getEncryptionGroup().getH();
        int tau = publicParameters.getSecurityParameters().getTau();

        int N = bold_e.size();
        List<BigInteger> bold_c = pi.getBold_c();
        List<BigInteger> bold_c_hat = pi.getBold_c_hat();
        BigInteger t_1 = pi.getT().getT_1();
        BigInteger t_2 = pi.getT().getT_2();
        BigInteger t_3 = pi.getT().getT_3();
        List<BigInteger> t_4 = pi.getT().getT_4();
        List<BigInteger> t_hat = pi.getT().getT_hat();
        BigInteger s_1 = pi.getS().getS_1();
        BigInteger s_2 = pi.getS().getS_2();
        BigInteger s_3 = pi.getS().getS_3();
        BigInteger s_4 = pi.getS().getS_4();
        List<BigInteger> s_hat = pi.getS().getS_hat();
        List<BigInteger> s_prime = pi.getS().getS_prime();

        // Size checks
        Preconditions.checkArgument(bold_e_prime.size() == N,
                "The length of bold_e_prime should be identical to that of bold_e");
        Preconditions.checkArgument(bold_c.size() == N,
                "The length of bold_c should be identical to that of bold_e");
        Preconditions.checkArgument(bold_c_hat.size() == N,
                "The length of bold_c_hat should be identical to that of bold_e");
        Preconditions.checkArgument(t_4.size() == 2,
                "t_4 should contain two elements");
        Preconditions.checkArgument(t_hat.size() == N,
                "The length of t_hat should be identical to that of bold_e");
        Preconditions.checkArgument(s_hat.size() == N,
                "The length of s_hat should be identical to that of bold_e");
        Preconditions.checkArgument(s_prime.size() == N,
                "The length of s_prime should be identical to that of bold_e");

        // Validity checks
        Preconditions.checkArgument(generalAlgorithms.isMember(t_1), "t_1 must be in G_q");
        Preconditions.checkArgument(generalAlgorithms.isMember(t_2), "t_2 must be in G_q");
        Preconditions.checkArgument(generalAlgorithms.isMember(t_3), "t_3 must be in G_q");
        Preconditions.checkArgument(t_4.parallelStream().allMatch(generalAlgorithms::isMember),
                "t_4_1 and t_4_2 be in G_q");
        Preconditions.checkArgument(t_hat.parallelStream().allMatch(generalAlgorithms::isMember),
                "all t_hat_i's must be in G_q");
        Preconditions.checkArgument(generalAlgorithms.isInZ_q(s_1), "s_1 must be in Z_q");
        Preconditions.checkArgument(generalAlgorithms.isInZ_q(s_2), "s_2 must be in Z_q");
        Preconditions.checkArgument(generalAlgorithms.isInZ_q(s_3), "s_3 must be in Z_q");
        Preconditions.checkArgument(generalAlgorithms.isInZ_q(s_4), "s_4 must be in Z_q");
        Preconditions.checkArgument(s_hat.parallelStream().allMatch(generalAlgorithms::isInZ_q),
                "all s_hat_i's must be in Z_q");
        Preconditions.checkArgument(s_prime.parallelStream().allMatch(generalAlgorithms::isInZ_q),
                "all s_prime_i's must be in Z_q");
        Preconditions.checkArgument(bold_c.parallelStream().allMatch(generalAlgorithms::isMember),
                "all c_i's must be in G_q");
        Preconditions.checkArgument(bold_c_hat.parallelStream().allMatch(generalAlgorithms::isMember),
                "all c_hat_i's must be in G_q");
        Preconditions.checkArgument(bold_e.parallelStream().allMatch(e -> generalAlgorithms.isMember(e.getA()) &&
                        generalAlgorithms.isMember(e.getB())),
                "all e_i's must be in G_q^2");
        Preconditions.checkArgument(bold_e_prime.parallelStream().allMatch(e -> generalAlgorithms.isMember(e.getA()) &&
                        generalAlgorithms.isMember(e.getB())),
                "all e_prime_i's must be in G_q^2");
        Preconditions.checkArgument(generalAlgorithms.isMember(pk), "pk must be in G_q");

        List<BigInteger> bold_h = generalAlgorithms.getGenerators(N);
        List<BigInteger> bold_u = generalAlgorithms.getNIZKPChallenges(N, new List[]{bold_e, bold_e_prime, bold_c}, tau);
        Object[] y = {bold_e, bold_e_prime, bold_c, bold_c_hat, pk};
        BigInteger c = generalAlgorithms.getNIZKPChallenge(y, pi.getT().elementsToHash(), tau);

        BigInteger c_prod = bold_c.stream().reduce(multiplyMod(p)).orElse(ONE);
        BigInteger h_prod = bold_h.stream().reduce(multiplyMod(p)).orElse(ONE);
        BigInteger c_bar = c_prod.multiply(h_prod.modInverse(p)).mod(p);

        BigInteger u = bold_u.stream().reduce(multiplyMod(q)).orElse(ONE);

        BigInteger c_hat = bold_c_hat.get(N - 1).multiply(modExp(h, u.negate(), p));
        BigInteger c_tilde = IntStream.range(0, N).mapToObj(i -> modExp(bold_c.get(i), bold_u.get(i), p))
                .reduce(multiplyMod(p)).orElse(ONE);

        BigInteger e_prime_1 = IntStream.range(0, N).mapToObj(i -> modExp(bold_e.get(i).getA(), bold_u.get(i), p))
                .reduce(multiplyMod(p)).orElse(ONE);
        BigInteger e_prime_2 = IntStream.range(0, N).mapToObj(i -> modExp(bold_e.get(i).getB(), bold_u.get(i), p))
                .reduce(multiplyMod(p)).orElse(ONE);

        BigInteger t_prime_1 = modExp(c_bar, c.negate(), p).multiply(modExp(g, s_1, p)).mod(p);
        BigInteger t_prime_2 = modExp(c_hat, c.negate(), p).multiply(modExp(g, s_2, p)).mod(p);
        BigInteger h_i_s_prime_i = IntStream.range(0, N).parallel()
                .mapToObj(i -> modExp(bold_h.get(i), s_prime.get(i), p))
                .reduce(multiplyMod(p)).orElse(ONE);
        BigInteger t_prime_3 = modExp(c_tilde, c.negate(), p).multiply(modExp(g, s_3, p)).multiply(h_i_s_prime_i).mod(p);

        BigInteger a_prime_i_s_prime_i = IntStream.range(0, N)
                .parallel()
                .mapToObj(i -> modExp(bold_e_prime.get(i).getA(), s_prime.get(i), p))
                .reduce(multiplyMod(p)).orElse(ONE);
        BigInteger t_prime_4_1 = modExp(e_prime_1, c.negate(), p)
                .multiply(modExp(pk, s_4.negate(), p))
                .multiply(a_prime_i_s_prime_i)
                .mod(p);
        BigInteger b_prime_i_s_prime_i = IntStream.range(0, N)
                .parallel()
                .mapToObj(i -> modExp(bold_e_prime.get(i).getB(), s_prime.get(i), p))
                .reduce(multiplyMod(p)).orElse(ONE);
        BigInteger t_prime_4_2 = modExp(e_prime_2, c.negate(), p)
                .multiply(modExp(g, s_4.negate(), p))
                .multiply(b_prime_i_s_prime_i)
                .mod(p);

        // add c_hat_0: h, thus offsetting the indices for c_hat by 1.
        List<BigInteger> tmp_bold_c_hat = new ArrayList<>();
        tmp_bold_c_hat.add(0, h);
        tmp_bold_c_hat.addAll(bold_c_hat);
        Map<Integer, BigInteger> t_hat_prime_map = IntStream.range(0, N).parallel().boxed()
                .collect(toMap(identity(), i -> modExp(tmp_bold_c_hat.get(i + 1), c.negate(), p)
                        .multiply(modExp(g, s_hat.get(i), p))
                        .multiply(modExp(tmp_bold_c_hat.get(i), s_prime.get(i), p))
                        .mod(p)));
        List<BigInteger> t_hat_prime = IntStream.range(0, N).mapToObj(t_hat_prime_map::get).collect(Collectors.toList());

        boolean isProofValid = t_1.compareTo(t_prime_1) == 0 &&
                t_2.compareTo(t_prime_2) == 0 &&
                t_3.compareTo(t_prime_3) == 0 &&
                t_4.get(0).compareTo(t_prime_4_1) == 0 &&
                t_4.get(1).compareTo(t_prime_4_2) == 0 &&
                IntStream.range(0, N).map(i -> t_hat.get(i).compareTo(t_hat_prime.get(i))).allMatch(i -> i == 0);
        if (!isProofValid) {
            log.error("Invalid proof found");
        }
        return isProofValid;
    }

    /**
     * Algorithm 7.49: GetPartialDecryptions
     *
     * @param bold_e ElGamal encryption of the votes
     * @param sk_j   the decryption key share for authority j
     * @return the list of the partial decryptions of the provided ElGamal encryptions, using key share sk_j
     */
    public List<BigInteger> getPartialDecryptions(List<Encryption> bold_e, BigInteger sk_j) {
        Preconditions.checkArgument(bold_e.parallelStream().allMatch(e -> generalAlgorithms.isMember(e.getA()) &&
                        generalAlgorithms.isMember(e.getB())),
                "all e_i's must be in G_q^2");
        BigInteger p = publicParameters.getEncryptionGroup().getP();
        return bold_e.stream().map(e_i -> modExp(e_i.getB(), sk_j, p)).collect(Collectors.toList());
    }

    /**
     * Algorithm 7.50: GenDecryptionProof
     *
     * @param sk_j         the private key share of authority j
     * @param pk_j         the public key share of authority j
     * @param bold_e       the vector of ElGamal encryptions
     * @param bold_b_prime the vector of partial ElGamal decryptions
     * @return a proof of knowledge for sk_j, satisfying <tt>b'_i = b_i ^ sk_j</tt> for all encryptions, and
     * <tt>pk_j = g ^ sk_j</tt>
     */
    public DecryptionProof genDecryptionProof(BigInteger sk_j, BigInteger pk_j, List<Encryption> bold_e,
                                              List<BigInteger> bold_b_prime) {
        Preconditions.checkArgument(generalAlgorithms.isInZ_q(sk_j), "sk_j must be in Z_q");
        Preconditions.checkArgument(generalAlgorithms.isMember(pk_j), "pk_j must be in G_q");
        Preconditions.checkArgument(bold_e.parallelStream().allMatch(e -> generalAlgorithms.isMember(e.getA()) &&
                        generalAlgorithms.isMember(e.getB())),
                "all e_i's must be in G_q^2");
        Preconditions.checkArgument(bold_b_prime.parallelStream().allMatch(generalAlgorithms::isMember),
                "all b_prime_i's must be in G_q^2");

        BigInteger p = publicParameters.getEncryptionGroup().getP();
        BigInteger q = publicParameters.getEncryptionGroup().getQ();
        BigInteger g = publicParameters.getEncryptionGroup().getG();
        BigInteger omega = randomGenerator.randomInZq(q);
        int tau = publicParameters.getSecurityParameters().getTau();

        BigInteger t_0 = modExp(g, omega, p);
        List<BigInteger> t = bold_e.stream().map(e_i -> modExp(e_i.getB(), omega, p)).collect(Collectors.toList());
        t.add(0, t_0);
        List<BigInteger> bold_b = bold_e.stream().map(Encryption::getB).collect(Collectors.toList());
        Object[] y = {pk_j, bold_b, bold_b_prime};
        BigInteger c = generalAlgorithms.getNIZKPChallenge(y, t.toArray(new BigInteger[0]), tau);
        BigInteger s = omega.add(c.multiply(sk_j)).mod(q);

        return new DecryptionProof(t, s);
    }
}
