import { useNavigate } from "react-router-dom";

export default function MainPage() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-gray-100 ml-[60px]">
      {/* Hero Section */}
      <section className="relative w-full px-6 sm:px-12 lg:px-24 pt-20 sm:pt-28 lg:pt-36 pb-16 sm:pb-24 bg-[radial-gradient(ellipse_155%_193%_at_50%_-2%,_rgba(242,242,246,0.70)_37%,_rgba(25,51,44,0.04)_100%)]">
        <div className="max-w-5xl mx-auto flex flex-col items-center gap-10 sm:gap-16 text-center">
          <h1 className="text-3xl sm:text-5xl md:text-6xl lg:text-7xl font-normal font-['BIZ_UDPMincho'] leading-tight">
            Empower Your Investments with Cutting-edge Insights
          </h1>

          <div className="flex flex-col sm:flex-row items-center gap-4">
            <button
              onClick={() => navigate("/feature/1")}
              className="px-6 py-3 bg-gray-800 rounded-3xl text-white text-base sm:text-lg font-normal font-['Roboto_Mono'] hover:bg-gray-700 transition-colors"
            >
              Get Started
            </button>
          </div>
        </div>
      </section>

      {/* Feature Sections */}
      <section className="w-full px-6 sm:px-12 lg:px-24 py-16 sm:py-24">
        <div className="max-w-6xl mx-auto flex flex-col gap-20 sm:gap-28">
          {/* Feature 1 */}
          <div className="flex flex-col lg:flex-row items-center gap-8 lg:gap-12">
            <img
              className="w-full max-w-md lg:max-w-lg h-auto aspect-video rounded-[10px] object-cover bg-zinc-300"
              src="https://placehold.co/900x500"
              alt="Real-time Market Tracking"
            />
            <div className="flex flex-col gap-4 text-center lg:text-left">
              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-normal font-['BIZ_UDPMincho'] leading-tight">
                Real-time Market Tracking
              </h2>
              <p className="text-base sm:text-lg lg:text-xl font-normal font-['Gothic_A1'] text-black leading-relaxed">
                Track market trends in real-time and make informed decisions.
              </p>
            </div>
          </div>

          {/* Feature 2 */}
          <div className="flex flex-col lg:flex-row-reverse items-center gap-8 lg:gap-12">
            <img
              className="w-full max-w-md lg:max-w-lg h-auto aspect-square rounded-[10px] object-cover bg-zinc-300"
              src="https://placehold.co/627x627"
              alt="AI-Powered Analysis"
            />
            <div className="flex flex-col gap-4 text-center lg:text-left">
              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-normal font-['BIZ_UDPMincho'] leading-tight">
                AI-Powered Analysis
              </h2>
              <p className="text-base sm:text-lg lg:text-xl font-normal font-['Gothic_A1'] text-black leading-relaxed">
                Leverage cutting-edge AI to analyze stocks and uncover insights.
              </p>
            </div>
          </div>

          {/* Feature 3 */}
          <div className="flex flex-col lg:flex-row items-center gap-8 lg:gap-12">
            <img
              className="w-full max-w-md lg:max-w-lg h-auto aspect-square rounded-[10px] object-cover bg-zinc-300"
              src="https://placehold.co/600x600"
              alt="Portfolio Management"
            />
            <div className="flex flex-col gap-4 text-center lg:text-left">
              <h2 className="text-2xl sm:text-3xl lg:text-4xl font-normal font-['BIZ_UDPMincho'] leading-tight">
                Portfolio Management
              </h2>
              <p className="text-base sm:text-lg lg:text-xl font-normal font-['Gothic_A1'] text-black leading-relaxed">
                Manage your portfolio efficiently with smart tools and dashboards.
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
