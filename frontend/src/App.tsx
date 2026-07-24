import { useState } from "react";
import { api, getToken } from "./api";
import Login from "./components/Login";
import Dashboard from "./components/Dashboard";

export default function App() {
  const [authed, setAuthed] = useState<boolean>(() => getToken() != null);

  function logout() {
    // Clears local tokens immediately; the server-side revocation is fire-and-forget.
    void api.logout();
    setAuthed(false);
  }

  return authed ? (
    <Dashboard onLogout={logout} />
  ) : (
    <Login onAuthed={() => setAuthed(true)} />
  );
}
