import { useState } from "react";
import api from "../api/apiClient";
import ApiResultBox from "../components/ApiResultBox";

export default function PingPage() {
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");

  const handlePing = async () => {
    setLoading(true);
    setErr("");
    try {
      const res = await api.get("test/ping");
      setResult(res);
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-[1920px] h-[5000px] relative bg-gray-100 overflow-hidden">
      <div className="w-[1920px] h-[781px] px-24 pt-24 left-0 top-[194px] absolute bg-[radial-gradient(ellipse_155.87%_193.34%_at_50.00%_-1.55%,_rgba(242,_242,_246,_0.70)_37%,_rgba(25,_51,_44.50,_0.04)_100%)]" />
      <div className="w-[1344px] left-[288px] top-[257px] absolute inline-flex flex-col justify-start items-center gap-24">
        <div className="self-stretch text-center justify-start text-black text-9xl font-normal font-['BIZ_UDPMincho'] leading-[120px]">
          Empower Your Investments with Cutting-edge Insights
        </div>
        <div className="px-6 py-3 bg-gray-800 rounded-3xl inline-flex justify-center items-center">
          <div className="justify-start text-white text-xl font-normal font-['Roboto_Mono'] leading-6">
            Get Started
          </div>
        </div>
        <div className="w-96 px-6 py-3 bg-gray-800 rounded-3xl inline-flex justify-center items-center">
          <div className="justify-start text-white text-xl font-normal font-['Roboto_Mono'] leading-6">
            Korean / English
          </div>
        </div>
      </div>
      <div className="w-[1920px] px-10 py-5 left-0 top-0 absolute inline-flex justify-between items-center">
        <div className="flex justify-start items-center gap-2.5">
          <img className="w-10 h-10" src="https://placehold.co/40x40" />
          <div className="justify-start text-black text-3xl font-medium font-['Noto_Sans_KR'] leading-[48px]">
            QAIMA
          </div>
        </div>
        <div className="flex justify-center items-center gap-9">
          <div className="justify-start text-black text-xl font-normal font-['Inter'] leading-8">
            sign up
          </div>
          <div className="px-5 py-0.5 bg-gray-800 rounded-[30px] flex justify-center items-center gap-2.5 overflow-hidden">
            <div className="justify-start text-white text-xl font-normal font-['Inter'] leading-8">
              sign in
            </div>
          </div>
        </div>
      </div>
      <div className="w-[1650px] left-[135px] top-[1165px] absolute inline-flex flex-col justify-center items-start gap-36">
        <div className="self-stretch inline-flex justify-between items-center">
          <div className="w-[600px] h-[500px] bg-zinc-300 rounded-[10px]" />
          <img
            className="w-[900px] h-[500px] rounded-[10px]"
            src="https://placehold.co/900x500"
          />
          <div className="w-[600px] inline-flex flex-col justify-start items-start gap-4">
            <div className="self-stretch justify-start text-black text-5xl font-normal font-['BIZ_UDPMincho'] leading-[52px]">
              Real-time Market Tracking
            </div>
            <div className="self-stretch justify-start text-black text-2xl font-normal font-['Gothic_A1'] leading-8">
              Track market trends in real-time and make informed decisions.
            </div>
          </div>
        </div>
        <div className="self-stretch inline-flex justify-between items-center">
          <div className="w-[600px] inline-flex flex-col justify-start items-start gap-4">
            <div className="self-stretch justify-start text-black text-5xl font-normal font-['BIZ_UDPMincho'] leading-[52px]">
              Real-time Market Tracking
            </div>
            <div className="self-stretch justify-start text-black text-2xl font-normal font-['Gothic_A1'] leading-8">
              Track market trends in real-time and make informed decisions.
            </div>
          </div>
          <div className="w-[600px] h-[500px] bg-zinc-300 rounded-[10px]" />
          <img
            className="w-[627px] h-[627px] rounded-[10px]"
            src="https://placehold.co/627x627"
          />
        </div>
        <div className="self-stretch inline-flex justify-between items-center">
          <div className="w-[600px] h-[500px] bg-zinc-300 rounded-[10px]" />
          <img
            className="w-[600px] h-[600px] rounded-[10px]"
            src="https://placehold.co/600x600"
          />
          <div className="w-[600px] inline-flex flex-col justify-start items-start gap-4">
            <div className="self-stretch justify-start text-black text-5xl font-normal font-['BIZ_UDPMincho'] leading-[52px]">
              Real-time Market Tracking
            </div>
            <div className="self-stretch justify-start text-black text-2xl font-normal font-['Gothic_A1'] leading-8">
              Track market trends in real-time and make informed decisions.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
