import { useState } from "react";
import { getToken, setToken } from "./api";
import Login from "./components/Login";
import Dashboard from "./components/Dashboard";

export default function App() {
  const [authed, setAuthed] = useState<boolean>(() => getToken() != null);

  function logout() {
    setToken(null);
    setAuthed(false);
  }

  return authed ? (
    <Dashboard onLogout={logout} />
  ) : (
    <Login onAuthed={() => setAuthed(true)} />
  );
}
