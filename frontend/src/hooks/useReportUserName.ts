import { useEffect, useState } from "react";
import { getMyProfile } from "../api/user";
import { getUser } from "../api/userStore";
import { isLoggedIn } from "../utils/auth";

export default function useReportUserName() {
  const [name, setName] = useState(getUser()?.name ?? "사용자");

  useEffect(() => {
    if (!isLoggedIn()) return;
    let alive = true;
    getMyProfile()
      .then((profile) => {
        if (alive) setName(profile.name || "사용자");
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  return name;
}
