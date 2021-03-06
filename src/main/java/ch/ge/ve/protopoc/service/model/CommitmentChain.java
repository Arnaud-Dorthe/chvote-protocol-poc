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

package ch.ge.ve.protopoc.service.model;

import com.google.common.collect.ImmutableList;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Model class representing a commitment chain, as returned by algorithm 5.47
 */
public final class CommitmentChain {
    private final List<BigInteger> bold_c;
    private final List<BigInteger> bold_r;

    public CommitmentChain(List<BigInteger> bold_c, List<BigInteger> bold_r) {
        this.bold_c = ImmutableList.copyOf(bold_c);
        this.bold_r = ImmutableList.copyOf(bold_r);
    }

    public List<BigInteger> getBold_c() {
        return ImmutableList.copyOf(bold_c);
    }

    public List<BigInteger> getBold_r() {
        return ImmutableList.copyOf(bold_r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommitmentChain that = (CommitmentChain) o;
        return Objects.equals(bold_c, that.bold_c) &&
                Objects.equals(bold_r, that.bold_r);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bold_c, bold_r);
    }
}
