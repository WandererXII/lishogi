import { ROLES } from 'shogiops/constants';
import { roleToForsyth as shogiopsRoleToForsyth } from 'shogiops/sfen';
import type { Role, Rules } from 'shogiops/types';
import { promote as shogiopsPromote, unpromote as shogiopsUnpromote } from 'shogiops/variant/util';

export function roleToForsyth(rules: Rules): (role: string) => string | undefined {
  return adaptRole(shogiopsRoleToForsyth(rules));
}

export function promote(rules: Rules): (role: string) => Role | undefined {
  return adaptRole(shogiopsPromote(rules));
}

export function unpromote(rules: Rules): (role: string) => Role | undefined {
  return adaptRole(shogiopsUnpromote(rules));
}

function adaptRole<T>(convert: (role: Role) => T): (role: string) => T | undefined {
  return role => {
    const knownRole = ROLES.find(candidate => candidate === role);
    return knownRole === undefined ? undefined : convert(knownRole);
  };
}
