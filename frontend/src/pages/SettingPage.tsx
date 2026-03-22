import { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function SettingPage() {
  const navigate = useNavigate();

  const [investLevel, setInvestLevel] = useState<
    "초급자" | "중급자" | "고급자"
  >("초급자");
  const [watchlist, setWatchlist] = useState<string[]>([
    "삼성전자",
    "TSLA",
    "SK하이닉스",
  ]);
  const [isEditingWatchlist, setIsEditingWatchlist] = useState(false);
  const [language, setLanguage] = useState<"한국어" | "English">("한국어");

  return (
    <div className="min-h-screen bg-[#FDFDFD] ml-[90px]">
      <header className="w-full bg-white border-b border-neutral-200 px-3 sm:px-4 py-2.5 sm:py-3 flex items-center">
        <h1 className="text-lg sm:text-xl md:text-2xl font-semibold text-black">
          내정보
        </h1>
      </header>

      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8 flex flex-col gap-8">
        {/* 기본정보 */}
        <div className="flex flex-col items-center gap-4">
          <h2 className="text-xl font-medium text-black">기본정보</h2>
          <div className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 text-base text-black">
            <span className="text-right border-r border-black pr-5 py-1">
              이름
            </span>
            <span className="py-1">홍길동</span>
            <span className="text-right border-r border-black pr-5 py-1">
              아이디(이메일)
            </span>
            <span className="py-1">honggildong123@naver.com</span>
            <span className="text-right border-r border-black pr-5 py-1">
              전화번호
            </span>
            <span className="py-1">010 1234 5678</span>
            <span className="text-right border-r border-black pr-5 py-1">
              생년월일
            </span>
            <span className="py-1">1999년 99월 99일</span>
          </div>
          <div className="w-full flex justify-end">
            <button
              onClick={() => navigate("/setting/edit")}
              className="border border-zinc-400 rounded-md px-4 py-2 text-sm hover:bg-zinc-50"
            >
              개인정보 수정
            </button>
          </div>
        </div>

        <hr className="border-black" />

        {/* 투자레벨 */}
        <div className="flex flex-col items-center gap-4">
          <h2 className="text-xl font-medium text-black">투자레벨</h2>
          <select
            value={investLevel}
            onChange={(e) =>
              setInvestLevel(e.target.value as "초급자" | "중급자" | "고급자")
            }
            className="border border-black rounded px-3 py-2 text-base bg-white focus:outline-none"
          >
            <option value="초급자">초급자</option>
            <option value="중급자">중급자</option>
            <option value="고급자">고급자</option>
          </select>
          <p className="text-xs text-zinc-500 text-center">
            투자 설문 결과를 기반으로 자동 설정되었습니다. 직접 변경할 수
            있습니다.
          </p>
        </div>

        <hr className="border-black" />

        {/* 투자성향 */}
        <div className="flex flex-col items-center gap-4">
          <h2 className="text-xl font-medium text-black">투자성향</h2>
          <p className="text-center text-base text-black">
            Aggressive (수익 우선, 손실 감수)
          </p>
          <p className="text-xs text-zinc-500 text-center">
            투자 설문 결과를 기반으로 자동 설정되었습니다. 설문을 다시 하면
            변경할 수 있습니다.
          </p>
          <button
            onClick={() => navigate("/survey")}
            className="border border-zinc-400 rounded-md px-4 py-2 text-sm hover:bg-zinc-50"
          >
            설문 다시하기
          </button>
        </div>

        <hr className="border-black" />

        {/* 관심종목 */}
        <div className="flex flex-col items-center gap-4">
          <h2 className="text-xl font-medium text-black">관심종목</h2>
          <div className="w-full max-h-40 overflow-y-auto border border-stone-300 rounded-lg p-3">
            {watchlist.length === 0 ? (
              <p className="text-sm text-zinc-500 text-center py-2">
                관심 종목이 없습니다.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {watchlist.map((item) =>
                  isEditingWatchlist ? (
                    <div
                      key={item}
                      className="flex items-center gap-1 px-2.5 py-1 bg-neutral-200 rounded-full"
                    >
                      <span className="text-sm font-medium text-black">
                        {item}
                      </span>
                      <button
                        onClick={() =>
                          setWatchlist((prev) => prev.filter((w) => w !== item))
                        }
                        className="text-zinc-500 hover:text-red-500 text-xs font-bold ml-1"
                      >
                        ✕
                      </button>
                    </div>
                  ) : (
                    <span
                      key={item}
                      className="px-2.5 py-1 bg-neutral-200 rounded-full text-sm font-medium text-black"
                    >
                      {item}
                    </span>
                  ),
                )}
              </div>
            )}
          </div>
          <button
            onClick={() => setIsEditingWatchlist((prev) => !prev)}
            className="border border-zinc-400 rounded-md px-4 py-2 text-sm hover:bg-zinc-50"
          >
            {isEditingWatchlist ? "선택삭제 완료" : "종목 선택삭제"}
          </button>
        </div>

        <hr className="border-black" />

        {/* 언어 */}
        <div className="flex flex-col items-center gap-4">
          <h2 className="text-xl font-medium text-black">언어/Language</h2>
          <select
            value={language}
            onChange={(e) =>
              setLanguage(e.target.value as "한국어" | "English")
            }
            className="border border-black rounded px-3 py-2 text-base bg-white focus:outline-none"
          >
            <option value="한국어">한국어</option>
            <option value="English">English</option>
          </select>
        </div>

        <hr className="border-black" />

        {/* 버그제보 */}
        <div className="flex flex-col items-center gap-4">
          <h2 className="text-xl font-medium text-black">버그제보</h2>
          <p className="text-center text-sm text-black">
            버그 제보 / contact : 9aima@gmail.com
          </p>
        </div>

        <hr className="border-black" />
      </div>
    </div>
  );
}
