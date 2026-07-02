/** The one registration rule HTML5 can't express: confirm must equal a non-empty password. */
export function passwordsMatch(password: string, confirm: string): boolean {
  return password.length > 0 && password === confirm
}
