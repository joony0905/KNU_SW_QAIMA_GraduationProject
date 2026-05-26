UPDATE dictionary
SET description_en = CASE term
        WHEN 'PER' THEN 'A valuation ratio that divides share price by earnings per share to assess the price level relative to earnings.'
        WHEN 'PBR' THEN 'A valuation ratio that divides share price by book value per share to assess the price level relative to net assets.'
        WHEN 'PSR' THEN 'A valuation ratio that compares share price with sales and is often used for loss-making companies.'
        WHEN 'PEG' THEN 'A valuation measure that compares PER with earnings growth to judge value relative to growth.'
        WHEN 'EV' THEN 'A measure of total enterprise value that adjusts market capitalization for net debt.'
        WHEN 'EV/EBITDA' THEN 'A valuation multiple that divides enterprise value by EBITDA, reducing differences caused by capital structure.'
        WHEN 'EPS' THEN 'Earnings per share, calculated by dividing company profit by the number of shares.'
        WHEN 'BPS' THEN 'Book value per share, calculated by dividing net assets by the number of shares.'
        WHEN 'ROE' THEN 'A profitability metric showing how much profit a company generated from shareholders equity.'
        WHEN 'ROA' THEN 'A profitability metric showing how much profit a company generated from total assets.'
        WHEN 'EBITDA' THEN 'Earnings before interest, taxes, depreciation, and amortization, used to assess cash-generating ability.'
        WHEN 'EBIT' THEN 'Earnings before interest and taxes, a profitability metric used to compare operating performance.'
        WHEN 'FCF' THEN 'Free cash flow, calculated by subtracting capital expenditures from operating cash flow.'
        WHEN 'CAPEX' THEN 'Capital expenditures for long-term assets such as facilities, equipment, and systems.'
        WHEN '영업현금흐름' THEN 'Cash flow showing actual cash inflows and outflows from the core business.'
        WHEN '투자활동현금흐름' THEN 'It is the cash flow from investment activities, such as facility investment or equity investment.'
        WHEN '재무활동현금흐름' THEN 'Cash flow related to financing activities, including borrowing, dividends, and capital increases.'
        WHEN '기준금리' THEN 'The policy rate used by a central bank as the benchmark for monetary policy operations.'
        WHEN '물가상승률' THEN 'The degree to which the overall price level rises over a given period.'
        WHEN '인플레이션' THEN 'A sustained rise in the overall price level.'
        WHEN '디플레이션' THEN 'A sustained decline in the overall price level.'
        WHEN 'GDP' THEN 'The total value of final goods and services produced domestically over a given period.'
        WHEN '환율' THEN 'The exchange ratio between one countrys currency and another countrys currency.'
        WHEN '경상수지' THEN 'An external balance that combines goods, services, income, and transfer transactions.'
        WHEN '유동성' THEN 'The ease with which an asset can be converted into cash without a large loss.'
        WHEN '수익률곡선' THEN 'A curve connecting interest rates by maturity, used to read market expectations.'
        WHEN '장단기금리차' THEN 'The gap between long-term and short-term interest rates, used to gauge economic expectations.'
        WHEN 'KOFR' THEN 'A risk-free reference rate calculated from government bond repo transactions.'
        WHEN 'CBDC' THEN 'Digital legal tender issued by a central bank.'
        WHEN '연착륙' THEN 'A situation where an overheated economy slows without a major shock.'
        WHEN '경기침체' THEN 'A phase in which production, consumption, and investment broadly contract.'
        WHEN '보통주' THEN 'These are basic shares that generally have voting rights and dividend participation rights.'
        WHEN '우선주' THEN 'These are stocks that have priority in dividends or distribution of remaining assets.'
        WHEN '자기주식' THEN 'This refers to the company''s own company stock.'
        WHEN '유상증자' THEN 'Issuing new stocks is a way to bring money into the company.'
        WHEN '무상증자' THEN 'New stocks are distributed using existing capital surplus, etc.'
        WHEN '주식배당' THEN 'This is a method of paying dividends in stocks instead of cash.'
        WHEN '사업보고서' THEN 'This is a report in which listed companies regularly disclose their business and financial status.'
        WHEN '분기보고서' THEN 'This is a regular report that discloses quarterly performance and major status.'
        WHEN '반기보고서' THEN 'This is a regular report containing financial and business status on a semi-annual basis.'
        WHEN '증권신고서' THEN 'This is a disclosure that contains key information to be provided to investors when issuing securities.'
        WHEN '대량보유보고' THEN 'This is a disclosure of shares made when holding more than a certain percentage of stocks.'
        WHEN '공개매수' THEN 'This is a method of purchasing stocks from an unspecified number of shareholders under open conditions.'
        WHEN 'CB' THEN 'These are corporate bonds with the right to convert them into stocks.'
        WHEN 'BW' THEN 'These are corporate bonds with the right to purchase new shares.'
        WHEN 'EB' THEN 'These are bonds with the right to exchange for stocks of another company.'
        WHEN '우리사주' THEN 'It is a system in which executives and employees acquire and hold company stocks.'
        WHEN 'ETF' THEN 'It is a fund designed to track an index and traded on the exchange.'
        WHEN 'ETN' THEN 'These are exchange-traded securities issued by securities companies and linked to index returns.'
        WHEN 'ELS' THEN 'These are stock-linked securities whose returns vary depending on the price of the underlying asset.'
        WHEN 'ELB' THEN 'It is a stock-linked derivative bond with a principal payment structure.'
        WHEN 'REIT' THEN 'It is a real estate investment company that invests in real estate and distributes profits.'
        WHEN 'ISA' THEN 'It is a tax-saving personal account that manages multiple financial products in one account.'
        WHEN 'IRP' THEN 'It is an individual retirement pension account that manages retirement funds and additional savings.'
        WHEN '연금저축' THEN 'This is a pension account that provides tax deduction benefits to save money for retirement.'
        WHEN '분배금' THEN 'This is the amount that a portion of the profits generated by a fund or ETF is distributed to investors.'
        WHEN '배당금' THEN 'It is cash or stock that a company distributes a portion of its profits to shareholders.'
        WHEN '공매도' THEN 'It is a trading method in which stocks are borrowed, sold, and later bought and repaid.'
        WHEN '대차거래' THEN 'It is a transaction of borrowing and lending stocks and is often linked to short selling.'
        WHEN 'NAV' THEN 'This is the net asset value per fund divided by the number of shares.'
        WHEN '지표가치' THEN 'It is a reference indicator that represents the real value per security of ETN.'
        WHEN '괴리율' THEN 'It shows how different the market price is from the net asset value or index value.'
        WHEN '추적오차' THEN 'It indicates how much the product return deviates from the target index.'
        WHEN 'LP' THEN 'It refers to a liquidity provider that presents quotes for smooth trading.'
        WHEN '호가스프레드' THEN 'It is the price difference between the bid price and the ask price.'
        WHEN 'VI' THEN 'It is a device that switches to single price trading in the event of sudden price fluctuations.'
        WHEN '레버리지ETF' THEN 'It is an ETF designed to track the multiple of the daily return of the underlying index.'
        WHEN '인버스ETF' THEN 'This ETF is designed to generate profits when the underlying index falls.'
        WHEN 'PEER CLUSTER' THEN 'It refers to a bundle of stocks with similar business structures or characteristics.'
        WHEN 'MARKET SNAPSHOT' THEN 'This is summary data that shows the market indicators of a specific stock at once.'
        WHEN 'SHARES OUTSTANDING' THEN 'This refers to the number of issued shares reflected in the current market calculation.'
        WHEN 'MARKET CAP' THEN 'It is the market value calculated by multiplying the company''s stock price by the number of issued shares.'
        WHEN 'FLOAT RATIO' THEN 'This is the proportion of issued stocks that can be distributed in the actual market.'
        WHEN 'FLOAT MARKET CAP' THEN 'It is the market value of a company based on distribution, calculated by multiplying the number of floating shares by the stock price.'
        WHEN 'TREASURY RATIO' THEN 'This is the proportion of treasury stocks among issued stocks.'
        WHEN '명목금리' THEN 'It is a superficial interest rate that does not reflect price changes.'
        WHEN '실질금리' THEN 'This is the actual interest rate level that reflects the impact of inflation in the nominal interest rate.'
        WHEN '중립금리' THEN 'It is an interest rate at a balanced level that neither overheats nor contracts the economy.'
        WHEN '정책금리' THEN 'It is the standard interest rate that the central bank adjusts to operate monetary policy.'
        WHEN '통화량' THEN 'It is the total amount of monetary assets, including currency and deposits, circulating within the economy.'
        WHEN '본원통화' THEN 'It is the sum of cash and reserve deposits directly supplied by the central bank.'
        WHEN '광의통화' THEN 'It is a currency index that broadly includes financial products in addition to cash and deposits.'
        WHEN '협의통화' THEN 'It is an indicator that bundles currencies that can be used immediately, such as cash and settlement deposits.'
        WHEN 'M1' THEN 'This refers to a currency with high settlement properties, such as cash and demand deposits.'
        WHEN 'M2' THEN 'It is a currency that includes cash and deposits as well as deposit assets that are difficult to deposit and withdraw at any time.'
        WHEN '지급준비제도' THEN 'It is a system that obliges banks to deposit a portion of their deposits with the central bank.'
        WHEN '지급준비율' THEN 'This is the percentage of deposits that must be deposited at the central bank.'
        WHEN '공개시장운영' THEN 'It is a policy tool through which the central bank regulates market liquidity through securities sales.'
        WHEN '재할인' THEN 'The central bank supplies funds by discounting bills held by financial institutions.'
        WHEN '양적완화' THEN 'This is a policy to significantly increase liquidity through asset purchases in addition to the policy interest rate.'
        WHEN '양적긴축' THEN 'It is a policy for the central bank to reduce liquidity by reducing its holdings.'
        WHEN '테이퍼링' THEN 'It is a process of normalizing monetary policy that gradually reduces the scale of asset purchases.'
        WHEN '기대인플레이션' THEN 'This is the rate of inflation expected by economic entities in the future.'
        WHEN '디스인플레이션' THEN 'Although prices are rising, the rate of increase is slowing down.'
        WHEN '스태그플레이션' THEN 'This is a phenomenon where an economic slump and rising prices occur simultaneously.'
        WHEN '총수요' THEN 'It is the sum of the demand for goods and services that households, businesses, government, and overseas sectors want to purchase.'
        WHEN '총공급' THEN 'It is the sum of the supply of goods and services that the entire economy can provide.'
        WHEN '잠재성장률' THEN 'It is the economy''s long-term growth ability that can be achieved without price instability.'
        WHEN '외환보유액' THEN 'It is a foreign currency asset held for external payments and market stability.'
        WHEN '통화스왑' THEN 'It is a contract in which two countries exchange currencies under agreed terms.'
        WHEN '신용스프레드' THEN 'It refers to the difference in interest rates between bonds with different credit risks.'
        WHEN '기간프리미엄' THEN 'This is added to the long-term bond interest rate as compensation for maturity risk.'
        WHEN '명목GDP' THEN 'This is gross domestic product calculated based on current prices.'
        WHEN '실질GDP' THEN 'It is the gross domestic product that reflects actual changes in production by removing price fluctuations.'
        WHEN 'GDP 디플레이터' THEN 'It is an indicator of the overall price level calculated using GDP.'
        WHEN 'CPI' THEN 'It is an index that shows changes in prices of goods and services consumed by households.'
        WHEN 'PPI' THEN 'It is an index that shows changes in the prices of goods and services shipped by domestic producers.'
        WHEN '실업률' THEN 'This is the ratio of people without jobs among the economically active population.'
        WHEN '고용률' THEN 'It is the proportion of employed people among the working-age population.'
        WHEN '경기선행지수' THEN 'It is an index constructed to show the future economic trends in advance.'
        WHEN '경기동행지수' THEN 'It is an index that combines indicators that show the current economic situation.'
        WHEN '경기후행지수' THEN 'It is an index that combines indicators that move after economic changes.'
        WHEN '무위험지표금리' THEN 'This is a standard interest rate calculated based on short-term financial transactions with very low credit risk.'
        WHEN '주요사항보고서' THEN 'This is a report that periodically discloses important management events of listed companies.'
        WHEN '정정공시' THEN 'This is a re-notification as there are errors or changes in the already submitted disclosure.'
        WHEN '불성실공시' THEN 'This refers to a case where a listed corporation does not properly fulfill its disclosure obligations.'
        WHEN '관리종목' THEN 'This is a stock that requires investment caution as it does not meet financial or public disclosure requirements.'
        WHEN '상장폐지' THEN 'Trading ends when the exchange cancels the listing of the securities in question.'
        WHEN '주주배정유상증자' THEN 'It is a paid-in capital increase method that gives existing shareholders the opportunity to acquire new shares first.'
        WHEN '일반공모유상증자' THEN 'It is a paid-in capital increase method that publicly solicits an unspecified number of investors.'
        WHEN '제3자배정유상증자' THEN 'It is a paid-in capital increase method that allocates new shares only to specific investors.'
        WHEN '실권주' THEN 'This refers to new shares remaining because the allocated investors did not subscribe.'
        WHEN '액면분할' THEN 'This is a measure to increase the number of shares while lowering the par value of the shares.'
        WHEN '액면병합' THEN 'This is a measure to increase the par value and reduce the number of shares by combining several shares.'
        WHEN '무상감자' THEN 'It is a method of reducing capital without paying compensation.'
        WHEN '유상감자' THEN 'It is a method of reducing capital by paying compensation to shareholders.'
        WHEN '연결재무제표' THEN 'It is a financial statement prepared by combining the parent company and subsidiary companies as if they were one economic entity.'
        WHEN '별도재무제표' THEN 'This is a financial statement prepared based on individual companies only.'
        WHEN '감사보고서' THEN 'This is a report containing the results of an external auditor''s audit of financial statements.'
        WHEN '적정의견' THEN 'This is the audit opinion that the financial statements have been prepared in accordance with accounting standards.'
        WHEN '한정의견' THEN 'Although there are limitations in some aspects, this is an audit opinion that is generally considered trustworthy.'
        WHEN '반대의견' THEN 'This is an audit opinion that determines that the financial statements do not comply with accounting standards.'
        WHEN '의견거절' THEN 'This is a case where the auditor does not express an opinion because he or she does not have sufficient evidence.'
        WHEN '최대주주' THEN 'This is the shareholder who holds the largest number of shares and thus influences control.'
        WHEN '특수관계인' THEN 'It refers to a person who has a close economic relationship in terms of control or interests.'
        WHEN '의결권' THEN 'It is the right to express one''s opinion for or against the agenda of the general shareholders'' meeting.'
        WHEN '의결권 없는 우선주' THEN 'These are preferred stocks that have priority rights to dividends but usually do not have voting rights.'
        WHEN '증권예탁증권' THEN 'It refers to a certificate of deposit issued on the basis of depositing original shares.'
        WHEN '신주인수권' THEN 'It is the right to acquire newly issued stocks under specified conditions.'
        WHEN '전환가액' THEN 'This is the standard price applied when converting convertible bonds into stocks.'
        WHEN '행사가액' THEN 'This is the price set at which stocks can be bought or sold when the right is exercised.'
        WHEN '전환청구권' THEN 'This is the right to request that convertible bonds, etc. be exchanged for stocks.'
        WHEN '주식매수선택권' THEN 'It is the right to buy stocks of one''s own company at a set price.'
        WHEN '내부자거래' THEN 'It refers to securities trading by a person who has access to important information inside the company.'
        WHEN '단기매매차익' THEN 'This refers to the profits earned by insiders from buying and selling within 6 months.'
        WHEN '미공개정보이용' THEN 'It is an act of trading using important information that has not been made public.'
        WHEN '특정증권등' THEN 'It is a category of securities that is grouped together by law, such as stock-related bonds and derivatives.'
        WHEN '교환사채권' THEN 'This refers to the exchange right granted to exchangeable bonds or the securities themselves.'
        WHEN '이익참가부사채' THEN 'This refers to corporate bonds with profit sharing conditions attached.'
        WHEN '임원거래계획보고' THEN 'It is a system in which executives or major shareholders report in advance transaction plans above a certain size.'
        WHEN '집합투자기구' THEN 'It is a fund-type organization that pools and manages funds from multiple investors.'
        WHEN '주식형펀드' THEN 'It is a fund that invests most of its assets in stocks.'
        WHEN '채권형펀드' THEN 'It is a fund that invests most of its assets in bonds.'
        WHEN '혼합형펀드' THEN 'It is a fund that invests in various assets such as stocks and bonds.'
        WHEN 'MMF' THEN 'It is a fund that focuses on liquidity by investing in short-term financial products.'
        WHEN 'TDF' THEN 'It is a pension fund that adjusts asset allocation according to the target time.'
        WHEN 'TR ETF' THEN 'This is an ETF that tracks total returns by reflecting reinvestment instead of distributions.'
        WHEN '합성ETF' THEN 'This is an ETF that replicates index returns using derivative contracts.'
        WHEN '현물ETF' THEN 'It is an ETF that tracks an index by directly incorporating actual underlying assets.'
        WHEN '파생형ETF' THEN 'This is an ETF that tracks target returns using derivatives such as futures.'
        WHEN '추적대상지수' THEN 'It is a reference index designed for ETFs or ETNs to follow.'
        WHEN '실시간지표가치' THEN 'It is an ETN fair value indicator calculated by reflecting intraday changes in the underlying index.'
        WHEN '중도상환' THEN 'This is a procedure to recover the investment by requesting repayment from the issuer before maturity.'
        WHEN '중도상환수수료' THEN 'This is a fee that may be applied when repaying early.'
        WHEN '발행회사신용위험' THEN 'This is a risk arising from default or credit deterioration of the securities company issuing the ETN.'
        WHEN '롤오버' THEN 'This is a transaction that transfers an expired futures position to the next maturity.'
        WHEN '롤오버비용' THEN 'This is a cost that occurs during the futures exchange process and affects the rate of return.'
        WHEN '선물' THEN 'It is a derivative contract to be traded at a set price at a specific point in the future.'
        WHEN '옵션' THEN 'It is a derivative product that trades the right to buy and sell under set conditions.'
        WHEN '콜옵션' THEN 'An option is trading the right to buy an underlying asset.'
        WHEN '풋옵션' THEN 'This is an option that trades the right to sell the underlying asset.'
        WHEN '위탁증거금' THEN 'This is the first deposit deposited for futures and options trading.'
        WHEN '유지증거금' THEN 'This is the minimum margin that must be maintained to maintain a position.'
        WHEN '반대매매' THEN 'This is a transaction in which collateral assets are forcibly disposed of due to lack of margin.'
        WHEN '신용융자' THEN 'This is a transaction in which a securities company lends purchase funds to an investor.'
        WHEN '신용거래' THEN 'This is a method in which an investor borrows funds or stocks from a securities company to trade.'
        WHEN '대주거래' THEN 'It is a borrowed transaction for the purpose of short selling, where stocks are borrowed and sold.'
        WHEN '동시호가' THEN 'This is a method in which orders received at the same time are executed at one price.'
        WHEN '단일가매매' THEN 'This is a method of gathering orders over a certain period of time and executing them at one price.'
        WHEN '시간외종가' THEN 'It is an after-hours method of trading based on the closing price before and after the market closes.'
        WHEN '시간외단일가' THEN 'It is an after-hours market where trading takes place at a single price for a certain period of time after the market closes.'
        WHEN '가격제한폭' THEN 'This is the maximum range of increase and decrease allowed in a day.'
        WHEN '상한가' THEN 'This means that the price has risen to the upper limit of the day.'
        WHEN '하한가' THEN 'This means that the price has fallen to the bottom of the price limit for the day.'
        WHEN '정규시장' THEN 'This market is held at the basic trading hours set by the exchange.'
        WHEN '시가총액가중방식' THEN 'It is an index calculation method in which the proportion of constituent stocks is determined based on market capitalization.'
        WHEN '동일가중방식' THEN 'It is an index calculation method that gives equal weight to constituent stocks.'
        WHEN '체결강도' THEN 'It is an indicator that shows the relative strength of buy and sell transactions.'
        WHEN '중앙은행' THEN 'It is a key national financial institution responsible for currency issuance and monetary policy.'
        WHEN '법정통화' THEN 'It is a currency recognized by the government as a means of debt payment.'
        WHEN '통화정책' THEN 'It is a policy to control interest rates and liquidity to stabilize prices and the economy.'
        WHEN '금융안정' THEN 'This refers to a state in which the financial system maintains its intermediary function even in the face of shocks.'
        WHEN '금융시스템' THEN 'It is a fund circulation structure that connects households, businesses, the government, and financial institutions.'
        WHEN '지급결제제도' THEN 'These are devices and rules that process fund transfers and payments between economic entities.'
        WHEN '결제리스크' THEN 'This is the risk that delivery of funds or securities will fail during the settlement process.'
        WHEN '시스템리스크' THEN 'There is a risk that the shock will spread throughout the financial system.'
        WHEN '통화승수' THEN 'It shows how much money the base currency increases through deposits and loans.'
        WHEN '신용경색' THEN 'Financial institutions are rapidly reducing their lending and funding supply.'
        WHEN '경기과열' THEN 'Demand has increased excessively, putting pressure on inflation and asset prices.'
        WHEN 'DSR' THEN 'It is a ratio that shows the burden of repaying all principal and interest compared to the borrower''s income.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        'PER',
        'PBR',
        'PSR',
        'PEG',
        'EV',
        'EV/EBITDA',
        'EPS',
        'BPS',
        'ROE',
        'ROA',
        'EBITDA',
        'EBIT',
        'FCF',
        'CAPEX',
        '영업현금흐름',
        '투자활동현금흐름',
        '재무활동현금흐름',
        '기준금리',
        '물가상승률',
        '인플레이션',
        '디플레이션',
        'GDP',
        '환율',
        '경상수지',
        '유동성',
        '수익률곡선',
        '장단기금리차',
        'KOFR',
        'CBDC',
        '연착륙',
        '경기침체',
        '보통주',
        '우선주',
        '자기주식',
        '유상증자',
        '무상증자',
        '주식배당',
        '사업보고서',
        '분기보고서',
        '반기보고서',
        '증권신고서',
        '대량보유보고',
        '공개매수',
        'CB',
        'BW',
        'EB',
        '우리사주',
        'ETF',
        'ETN',
        'ELS',
        'ELB',
        'REIT',
        'ISA',
        'IRP',
        '연금저축',
        '분배금',
        '배당금',
        '공매도',
        '대차거래',
        'NAV',
        '지표가치',
        '괴리율',
        '추적오차',
        'LP',
        '호가스프레드',
        'VI',
        '레버리지ETF',
        '인버스ETF',
        'PEER CLUSTER',
        'MARKET SNAPSHOT',
        'SHARES OUTSTANDING',
        'MARKET CAP',
        'FLOAT RATIO',
        'FLOAT MARKET CAP',
        'TREASURY RATIO',
        '명목금리',
        '실질금리',
        '중립금리',
        '정책금리',
        '통화량',
        '본원통화',
        '광의통화',
        '협의통화',
        'M1',
        'M2',
        '지급준비제도',
        '지급준비율',
        '공개시장운영',
        '재할인',
        '양적완화',
        '양적긴축',
        '테이퍼링',
        '기대인플레이션',
        '디스인플레이션',
        '스태그플레이션',
        '총수요',
        '총공급',
        '잠재성장률',
        '외환보유액',
        '통화스왑',
        '신용스프레드',
        '기간프리미엄',
        '명목GDP',
        '실질GDP',
        'GDP 디플레이터',
        'CPI',
        'PPI',
        '실업률',
        '고용률',
        '경기선행지수',
        '경기동행지수',
        '경기후행지수',
        '무위험지표금리',
        '주요사항보고서',
        '정정공시',
        '불성실공시',
        '관리종목',
        '상장폐지',
        '주주배정유상증자',
        '일반공모유상증자',
        '제3자배정유상증자',
        '실권주',
        '액면분할',
        '액면병합',
        '무상감자',
        '유상감자',
        '연결재무제표',
        '별도재무제표',
        '감사보고서',
        '적정의견',
        '한정의견',
        '반대의견',
        '의견거절',
        '최대주주',
        '특수관계인',
        '의결권',
        '의결권 없는 우선주',
        '증권예탁증권',
        '신주인수권',
        '전환가액',
        '행사가액',
        '전환청구권',
        '주식매수선택권',
        '내부자거래',
        '단기매매차익',
        '미공개정보이용',
        '특정증권등',
        '교환사채권',
        '이익참가부사채',
        '임원거래계획보고',
        '집합투자기구',
        '주식형펀드',
        '채권형펀드',
        '혼합형펀드',
        'MMF',
        'TDF',
        'TR ETF',
        '합성ETF',
        '현물ETF',
        '파생형ETF',
        '추적대상지수',
        '실시간지표가치',
        '중도상환',
        '중도상환수수료',
        '발행회사신용위험',
        '롤오버',
        '롤오버비용',
        '선물',
        '옵션',
        '콜옵션',
        '풋옵션',
        '위탁증거금',
        '유지증거금',
        '반대매매',
        '신용융자',
        '신용거래',
        '대주거래',
        '동시호가',
        '단일가매매',
        '시간외종가',
        '시간외단일가',
        '가격제한폭',
        '상한가',
        '하한가',
        '정규시장',
        '시가총액가중방식',
        '동일가중방식',
        '체결강도',
        '중앙은행',
        '법정통화',
        '통화정책',
        '금융안정',
        '금융시스템',
        '지급결제제도',
        '결제리스크',
        '시스템리스크',
        '통화승수',
        '신용경색',
        '경기과열',
        'DSR'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN 'LTV' THEN 'It refers to the ratio of the loanable amount to the collateral value.'
        WHEN 'DTI' THEN 'It is the ratio of the burden of repaying the mortgage loan principal and interest to income.'
        WHEN '명목실효환율' THEN 'It is a nominal exchange rate index calculated by reflecting the currencies of trading partners.'
        WHEN '실질실효환율' THEN 'It is an index of exchange rate competitiveness calculated by taking into account differences in price levels.'
        WHEN '실효환율' THEN 'It is a comprehensive exchange rate index calculated by weighting the average of several countries’ currencies.'
        WHEN '상품수지' THEN 'It is a balance of payments item that represents the difference between exports and imports of goods.'
        WHEN '서비스수지' THEN 'It is a balance of payments item for service transactions such as transportation, travel, and knowledge services.'
        WHEN '금융계정' THEN 'It is a balance of payments item that records changes in external financial assets and liabilities.'
        WHEN '경상흑자' THEN 'In the current account, exports and income inflows are greater than imports.'
        WHEN '경상적자' THEN 'In the current account balance, it means that expenditures are greater than income.'
        WHEN '외환시장' THEN 'It is a market where different currencies are bought and sold.'
        WHEN '환헤지' THEN 'This is a response to reduce changes in profits and losses due to exchange rate fluctuations.'
        WHEN '환노출' THEN 'Asset values ​​are affected by exchange rate fluctuations.'
        WHEN '외환스왑' THEN 'It is a transaction that exchanges currencies by combining spot exchange and forward exchange.'
        WHEN '스왑포인트' THEN 'It refers to the difference between the spot exchange rate and the forward exchange rate.'
        WHEN '국고채' THEN 'These are bonds issued by the government to raise finances.'
        WHEN '회사채' THEN 'These are bonds issued by companies to raise funds.'
        WHEN '장외시장' THEN 'It is a market where transactions are made by agreement between parties outside the exchange.'
        WHEN '단기자금시장' THEN 'It is a financial market that borrows and lends funds with short maturities.'
        WHEN '콜금리' THEN 'This is the interest rate applied to ultra-short-term fund transactions between financial institutions.'
        WHEN 'RP매매' THEN 'It is a short-term financial transaction in which bonds are bought and sold under repurchase conditions.'
        WHEN '주주총회' THEN 'This is a meeting where shareholders decide on the company''s major agenda.'
        WHEN '정기주주총회' THEN 'It is a general shareholders'' meeting held regularly after the end of the fiscal year.'
        WHEN '임시주주총회' THEN 'In addition to the regular general meeting, this is a general shareholders'' meeting held from time to time when necessary.'
        WHEN '이사회' THEN 'It is a meeting body of directors responsible for major management decisions of the company.'
        WHEN '사외이사' THEN 'This refers to external directors who are independent from the company''s full-time management.'
        WHEN '감사위원회' THEN 'This is a committee within the board of directors that supervises accounting and internal control.'
        WHEN '감사위원' THEN 'A director who performs audit functions as a member of the audit committee.'
        WHEN '지배주주' THEN 'Shareholders who can have a practical influence on the company''s decision-making.'
        WHEN '소수주주' THEN 'This refers to general shareholders who have a low stake but have shareholder rights.'
        WHEN '경영권분쟁' THEN 'This is a situation where stakeholders are competing for control of the company.'
        WHEN '합병' THEN 'It is a method of organizational reorganization that combines two or more companies into one.'
        WHEN '분할' THEN 'It is a method of reorganization that divides a company into two or more.'
        WHEN '인적분할' THEN 'It is a split method in which existing shareholders are allotted shares of the new company.'
        WHEN '물적분할' THEN 'Split is a split method in which the shares of the new company are held by the existing company.'
        WHEN '분할합병' THEN 'The structure is to divide a company and then merge part of it with another company.'
        WHEN '영업양수도' THEN 'It is a transaction in which a business or sales division is transferred or taken over.'
        WHEN '주식소각' THEN 'This is a measure to reduce the number of shares by eliminating issued shares.'
        WHEN '자사주취득' THEN 'This is the act of a company purchasing its own shares directly.'
        WHEN '자사주처분' THEN 'This means putting the company''s own stocks back on the market.'
        WHEN '신탁계약' THEN 'It is a contract to entrust the management or storage of assets to a financial institution.'
        WHEN '전자투표' THEN 'It is a system to exercise voting rights at general shareholders’ meetings electronically.'
        WHEN '전자위임장' THEN 'It is a system to submit the intention to delegate voting rights electronically.'
        WHEN '포괄적주식교환' THEN 'It is a method of organizational reorganization in which one company acquires all of the stocks of another company.'
        WHEN '포괄적주식이전' THEN 'It is a reorganization method in which existing companies jointly receive holding company stocks.'
        WHEN '종류주식' THEN 'This refers to stocks with different rights design.'
        WHEN '전환우선주' THEN 'These are preferred stocks that can be converted into common stocks, etc. under certain conditions.'
        WHEN '상환우선주' THEN 'Preferred stock is designed to be repaid by the company at a certain point in time.'
        WHEN '상환전환우선주' THEN 'It is a preferred stock with both redemption and conversion rights.'
        WHEN '전환상환우선주' THEN 'It refers to preferred stock with conversion and repayment conditions attached.'
        WHEN '의무보유확약' THEN 'It is a system in which one agrees not to sell stocks for a certain period of time.'
        WHEN '보호예수' THEN 'It is a device that increases market stability by restricting the disposal of stocks for a certain period of time.'
        WHEN 'SPAC' THEN 'It is a special purpose company created to merge with an unlisted company and list it.'
        WHEN '우회상장' THEN 'This is a method in which an unlisted company combines with a listed company to effectively achieve the listing effect.'
        WHEN '재감사' THEN 'This is a procedure to re-audit financial statements that have already been audited.'
        WHEN '불공정거래' THEN 'It is a transaction that undermines market order, such as market manipulation using undisclosed information.'
        WHEN '서킷브레이커' THEN 'It is a market stabilization device that temporarily suspends trading when the market changes suddenly.'
        WHEN '사이드카' THEN 'It is a device that limits program trading in the spot market when the futures market changes suddenly.'
        WHEN '프로그램매매' THEN 'It is a method of grouping multiple stocks and trading them according to automated rules.'
        WHEN '차익거래' THEN 'It is a transaction that pursues risk-free profit by using price differences.'
        WHEN '비차익거래' THEN 'It is a program trading method, not for profit purposes.'
        WHEN '시장가주문' THEN 'This is an order placed at the current market price without specifying a price.'
        WHEN '지정가주문' THEN 'This is an ordering method where you set the desired price.'
        WHEN '조건부지정가주문' THEN 'It is an order that remains at the limit price under certain conditions and then converts to the market price.'
        WHEN '최유리지정가주문' THEN 'This is an order placed at the most advantageous counter-quote at the time of ordering.'
        WHEN '최우선지정가주문' THEN 'It is a limit order issued at the current highest bid price.'
        WHEN 'IOC' THEN 'This is an order condition in which only the quantity that can be executed immediately is traded and the rest is canceled.'
        WHEN 'FOK' THEN 'This is an order condition in which the entire order is canceled if it is not executed immediately.'
        WHEN '장전시간외' THEN 'This is after-hours trading that takes place at a certain time before the start of the regular market.'
        WHEN '장후시간외' THEN 'This is after-hours trading that takes place at a certain time after the regular market closes.'
        WHEN '호가단위' THEN 'This is the minimum price interval applied when presenting an order price.'
        WHEN '거래정지' THEN 'This is a measure to suspend trading of specific stocks for a certain period of time.'
        WHEN '매매거래정지' THEN 'This is a measure to suspend trading for investor protection or confirmation of disclosure.'
        WHEN '공매도과열종목' THEN 'This is a stock that may face additional restrictions due to a rapid increase in short selling.'
        WHEN '공매도금지' THEN 'This is a measure to prevent short selling for a certain period of time.'
        WHEN '업틱룰' THEN 'This is a rule that allows short selling prices to be issued only at or above the previous closing price.'
        WHEN '호가잔량' THEN 'This is the quantity of buy and sell orders piled up in each price range.'
        WHEN '체결량' THEN 'This refers to the quantity for which the transaction was actually completed.'
        WHEN '거래대금' THEN 'It is the total transaction amount multiplied by the traded quantity and price.'
        WHEN '거래량' THEN 'It is the total quantity traded over a certain period of time.'
        WHEN '기초지수' THEN 'ETFs, ETNs, derivatives, etc. are standard indices that follow performance.'
        WHEN '정기변경' THEN 'The index constituents or weight are changed according to a set schedule.'
        WHEN '특별변경' THEN 'This is an exceptional adjustment to the index composition due to an unexpected event.'
        WHEN '리밸런싱' THEN 'The asset composition is readjusted to meet the target proportion.'
        WHEN 'INAV' THEN 'This is the estimated intraday net asset value of the ETF, a value calculated by reflecting the price of the underlying asset during trading hours.'
        WHEN '시장조성자' THEN 'A participant who continuously presents quotes to ensure smooth transactions.'
        WHEN '인덱스펀드' THEN 'It is a fund designed to track a specific index.'
        WHEN '공모펀드' THEN 'It is a fund that recruits an unspecified number of investors.'
        WHEN '사모펀드' THEN 'It is a fund that offers limited support to minority investors.'
        WHEN 'CMA' THEN 'It is a comprehensive asset management account designed to allow deposits and withdrawals at any time.'
        WHEN 'RP' THEN 'It refers to bond trading with a repurchase agreement and is often used for short-term management.'
        WHEN '랩어카운트' THEN 'It is an account service where securities companies receive and manage customer assets.'
        WHEN '퇴직연금' THEN 'It is a system to accumulate and manage retirement funds for retirement.'
        WHEN 'DB형퇴직연금' THEN 'It is a defined benefit retirement pension where the amount to be received upon retirement is predetermined.'
        WHEN 'DC형퇴직연금' THEN 'It is a retirement pension in which the company contribution is determined and the amount received varies depending on management performance.'
        WHEN '파생결합증권' THEN 'It is a security whose profit structure varies depending on the price of the underlying asset.'
        WHEN '파생결합사채' THEN 'It is a derivative-linked bond-type product that can include a principal payment structure.'
        WHEN 'DLS' THEN 'It is a derivative-linked security based on various assets such as interest rates, exchange rates, and raw materials.'
        WHEN 'DLB' THEN 'This refers to a derivative-linked bond with a principal payment structure.'
        WHEN '투자성향' THEN 'It is a characteristic that combines the investor’s risk tolerance and purpose.'
        WHEN '적합성원칙' THEN 'The principle is that financial companies should recommend products that suit investors’ preferences.'
        WHEN '적정성원칙' THEN 'The principle is that even if an investor directly requests something, an explanation must be provided if it is not appropriate.'
        WHEN '설명의무' THEN 'It is the duty of financial companies to fully explain the product structure and risks.'
        WHEN '분산투자' THEN 'It is an investment method that reduces concentration of specific risks by dividing assets and stocks.'
        WHEN '자산배분' THEN 'It is a strategy to manage profits and risks by dividing the proportion of each asset class.'
        WHEN '채권' THEN 'It is a security issued by the issuer with a promise to pay principal and interest.'
        WHEN '국채' THEN 'These are bonds issued by the government to raise finances.'
        WHEN '지방채' THEN 'These are bonds issued by local governments.'
        WHEN '액면가' THEN 'It refers to the standard amount indicated on the security.'
        WHEN '표면금리' THEN 'This is the agreed interest rate written on the bond certificate.'
        WHEN '이표채' THEN 'It is a bond that pays interest at set intervals.'
        WHEN '할인채' THEN 'These are bonds that generate profits by issuing them at a discount without paying any interest.'
        WHEN '복리채' THEN 'It is a bond that reflects a structure in which interest is added on top of interest.'
        WHEN '만기' THEN 'This refers to the point in time when the principal is repaid or the contract is terminated.'
        WHEN '만기일' THEN 'This refers to the end date of a bond or contract.'
        WHEN '듀레이션' THEN 'It is an indicator that shows the sensitivity of bond prices to changes in interest rates.'
        WHEN '수정듀레이션' THEN 'It is an indicator that approximates the change in bond price due to a one-unit change in interest rate.'
        WHEN '컨벡서티' THEN 'It is an indicator that shows the effect of bond price curvature due to interest rate changes.'
        WHEN '신용등급' THEN 'It is the result of a credit evaluation that indicates the ability to repay debt.'
        WHEN '투자등급' THEN 'This is a bond rating range that is considered to have a relatively low default risk.'
        WHEN '투기등급' THEN 'This is a bond rating range that is considered to have relatively high credit risk.'
        WHEN '디폴트' THEN 'It is in default of its obligations to pay principal and interest.'
        WHEN '부도위험' THEN 'It refers to the possibility that the debtor will not be able to repay the principal and interest.'
        WHEN '선순위채' THEN 'These are bonds that take precedence in the order of repayment in the event of bankruptcy or liquidation.'
        WHEN '후순위채' THEN 'These are bonds that are later in the order of repayment than general bonds.'
        WHEN '영구채' THEN 'These are bonds that have no maturity or are designed to have a very long maturity.'
        WHEN '메자닌' THEN 'It is a medium-sized financing method that combines the characteristics of stocks and bonds.'
        WHEN '하이일드채' THEN 'These are bonds with high credit risk in exchange for high interest rates.'
        WHEN '쿠폰' THEN 'This is an expression that refers to the interest or interest rate paid by a bond.'
        WHEN '쿠폰금리' THEN 'It refers to the ratio of agreed interest payment to face value.'
        WHEN '현재수익률' THEN 'The yield is the annual interest rate of a bond divided by its current price.'
        WHEN '채권가격' THEN 'It is the current price of a bond formed in market transactions.'
        WHEN '매출액' THEN 'It is the total profit a company earns from selling goods or services.'
        WHEN '매출총이익' THEN 'Profit is calculated by subtracting the cost of sales from sales.'
        WHEN '매출총이익률' THEN 'It is the ratio of gross profit to sales.'
        WHEN '영업이익' THEN 'It refers to profits earned from main business activities.'
        WHEN '영업이익률' THEN 'It is the ratio of operating profit to sales.'
        WHEN '당기순이익' THEN 'It is the final profit that reflects all costs and taxes during the accounting period.'
        WHEN '순이익률' THEN 'It is the ratio of net profit to sales.'
        WHEN '매출원가' THEN 'This is the cost directly incurred to sell a product or product.'
        WHEN '판관비' THEN 'It is an expense item that combines selling expenses and administrative expenses.'
        WHEN '유동자산' THEN 'It is an asset that can be converted into cash or used within one year.'
        WHEN '비유동자산' THEN 'It is an asset held or used beyond one year.'
        WHEN '유동부채' THEN 'It is a debt that must be repaid within one year.'
        WHEN '비유동부채' THEN 'It is a debt whose repayment date is more than one year.'
        WHEN '자본총계' THEN 'It is the total net worth of assets minus liabilities.'
        WHEN '부채총계' THEN 'It is the sum of all liabilities borne by a company.'
        WHEN '부채비율' THEN 'It is a ratio that shows the amount of debt compared to equity capital.'
        WHEN '유동비율' THEN 'Short-term solvency is seen as the ratio of current assets to current liabilities.'
        WHEN '당좌비율' THEN 'It is the ratio of current liabilities to current assets excluding inventory.'
        WHEN '이자보상배율' THEN 'It shows how much interest expenses are covered by operating profit.'
        WHEN '순차입금' THEN 'It is the real borrowing burden calculated by subtracting cash equivalents from total borrowings.'
        WHEN '총차입금' THEN 'It is the sum of both short-term and long-term borrowings.'
        WHEN '자기자본비율' THEN 'It is the ratio of equity capital to total assets.'
        WHEN '총자산회전율' THEN 'It is an efficiency indicator that shows how much sales were raised by utilizing total assets.'
        WHEN '재고자산회전율' THEN 'It shows how many times inventory has been turned over over a certain period of time.'
        WHEN '매출채권회전율' THEN 'It is a turnover indicator that shows the speed of collection of accounts receivable.'
        WHEN '현금및현금성자산' THEN 'It is an asset that can be used immediately or easily converted into cash.'
        WHEN '배당수익률' THEN 'It refers to the ratio of dividends to stock prices.'
        WHEN '배당성향' THEN 'This is the percentage of net profit paid out as dividends.'
        WHEN '중간배당' THEN 'This is a dividend paid out in the middle of the fiscal year.'
        WHEN '결산배당' THEN 'This is a regular dividend paid out after the fiscal year ends.'
        WHEN '기준가격' THEN 'This refers to the evaluation standard price per fund or per share.'
        WHEN '환매' THEN 'This is a procedure for fund investors to cancel in order to get their funds back.'
        WHEN '환매수수료' THEN 'This is a cost that may be charged when redeeming a fund.'
        WHEN '선취수수료' THEN 'This is a sales fee that is charged from the beginning when signing up for an investment.'
        WHEN '후취수수료' THEN 'This is a sales fee charged at the time of redemption after holding.'
        WHEN '총보수' THEN 'This is the sum of various fees incurred in operating the fund.'
        WHEN '운용보수' THEN 'This is the compensation that an asset manager receives in return for management.'
        WHEN '판매보수' THEN 'This is the compensation the seller receives in return for investor services.'
        WHEN '수탁보수' THEN 'This is the compensation that the trustee receives in exchange for storing and managing assets.'
        WHEN '사무관리보수' THEN 'This is compensation received in return for calculating the standard price and processing administrative procedures.'
        WHEN '보수차감후수익률' THEN 'This is the actual rate of return after reflecting various compensation and expenses.'
        WHEN '설정액' THEN 'It refers to the amount of funds based on the principal collected in the fund.'
        WHEN '설정일' THEN 'This is the date when the fund or product was first established.'
        WHEN '상장좌수' THEN 'This is the number or quantity of listed ETF ETN.'
        WHEN '운용규모' THEN 'This is the amount of money that a fund or product actually operates.'
        WHEN 'KOSPI' THEN 'It is an English notation for the representative index of the stock market.'
        WHEN 'KOSDAQ' THEN 'This is the English notation for the representative index of the KOSDAQ market.'
        WHEN 'KONEX' THEN 'This is the English notation for KONEX Market.'
        WHEN '코스피200' THEN 'It is a core index composed of 200 representative stocks in the stock market.'
        WHEN '중형주' THEN 'It refers to a group of stocks with a medium market capitalization.'
        WHEN '소형주' THEN 'It refers to a group of stocks with a relatively small market capitalization.'
        WHEN '대형주' THEN 'It refers to a group of representative stocks with a large market capitalization.'
        WHEN '가치주' THEN 'It is a stock that is evaluated as having a low price compared to its profit assets.'
        WHEN '성장주' THEN 'This is a stock whose high expectations for future growth are reflected in its stock price.'
        WHEN '배당주' THEN 'This refers to stocks with a high dividend payout ratio or dividend yield.'
        WHEN '방어주' THEN 'It is a stock whose performance and stock price are relatively stable despite economic fluctuations.'
        WHEN '경기민감주' THEN 'It is a stock whose performance and stock price fluctuate greatly depending on the economic flow.'
        WHEN '테마주' THEN 'It is a group of stocks tied to specific issues or industry expectations.'
        WHEN '섹터' THEN 'It is a bundle of stocks classified by similar industries or business characteristics.'
        WHEN '업종' THEN 'It is an industry classification based on the content of business activities.'
        WHEN '지수편입' THEN 'A specific stock is newly added to the index constituents.'
        WHEN '지수편출' THEN 'Certain stocks are excluded from the index constituents.'
        WHEN '포트폴리오' THEN 'It refers to an investment bundle that combines several assets and stocks.'
        WHEN '벤치마크' THEN 'It is an index or portfolio used as a standard for performance comparison.'
        WHEN '초과수익' THEN 'It means more profit earned compared to the benchmark.'
        WHEN '절대수익' THEN 'This is profit generated independently, regardless of the object of comparison.'
        WHEN '상대수익' THEN 'Return compared to a reference index or competing strategy.'
        WHEN '연환산수익률' THEN 'This is the rate of return over a certain period of time converted to one year.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        'LTV',
        'DTI',
        '명목실효환율',
        '실질실효환율',
        '실효환율',
        '상품수지',
        '서비스수지',
        '금융계정',
        '경상흑자',
        '경상적자',
        '외환시장',
        '환헤지',
        '환노출',
        '외환스왑',
        '스왑포인트',
        '국고채',
        '회사채',
        '장외시장',
        '단기자금시장',
        '콜금리',
        'RP매매',
        '주주총회',
        '정기주주총회',
        '임시주주총회',
        '이사회',
        '사외이사',
        '감사위원회',
        '감사위원',
        '지배주주',
        '소수주주',
        '경영권분쟁',
        '합병',
        '분할',
        '인적분할',
        '물적분할',
        '분할합병',
        '영업양수도',
        '주식소각',
        '자사주취득',
        '자사주처분',
        '신탁계약',
        '전자투표',
        '전자위임장',
        '포괄적주식교환',
        '포괄적주식이전',
        '종류주식',
        '전환우선주',
        '상환우선주',
        '상환전환우선주',
        '전환상환우선주',
        '의무보유확약',
        '보호예수',
        'SPAC',
        '우회상장',
        '재감사',
        '불공정거래',
        '서킷브레이커',
        '사이드카',
        '프로그램매매',
        '차익거래',
        '비차익거래',
        '시장가주문',
        '지정가주문',
        '조건부지정가주문',
        '최유리지정가주문',
        '최우선지정가주문',
        'IOC',
        'FOK',
        '장전시간외',
        '장후시간외',
        '호가단위',
        '거래정지',
        '매매거래정지',
        '공매도과열종목',
        '공매도금지',
        '업틱룰',
        '호가잔량',
        '체결량',
        '거래대금',
        '거래량',
        '기초지수',
        '정기변경',
        '특별변경',
        '리밸런싱',
        'INAV',
        '시장조성자',
        '인덱스펀드',
        '공모펀드',
        '사모펀드',
        'CMA',
        'RP',
        '랩어카운트',
        '퇴직연금',
        'DB형퇴직연금',
        'DC형퇴직연금',
        '파생결합증권',
        '파생결합사채',
        'DLS',
        'DLB',
        '투자성향',
        '적합성원칙',
        '적정성원칙',
        '설명의무',
        '분산투자',
        '자산배분',
        '채권',
        '국채',
        '지방채',
        '액면가',
        '표면금리',
        '이표채',
        '할인채',
        '복리채',
        '만기',
        '만기일',
        '듀레이션',
        '수정듀레이션',
        '컨벡서티',
        '신용등급',
        '투자등급',
        '투기등급',
        '디폴트',
        '부도위험',
        '선순위채',
        '후순위채',
        '영구채',
        '메자닌',
        '하이일드채',
        '쿠폰',
        '쿠폰금리',
        '현재수익률',
        '채권가격',
        '매출액',
        '매출총이익',
        '매출총이익률',
        '영업이익',
        '영업이익률',
        '당기순이익',
        '순이익률',
        '매출원가',
        '판관비',
        '유동자산',
        '비유동자산',
        '유동부채',
        '비유동부채',
        '자본총계',
        '부채총계',
        '부채비율',
        '유동비율',
        '당좌비율',
        '이자보상배율',
        '순차입금',
        '총차입금',
        '자기자본비율',
        '총자산회전율',
        '재고자산회전율',
        '매출채권회전율',
        '현금및현금성자산',
        '배당수익률',
        '배당성향',
        '중간배당',
        '결산배당',
        '기준가격',
        '환매',
        '환매수수료',
        '선취수수료',
        '후취수수료',
        '총보수',
        '운용보수',
        '판매보수',
        '수탁보수',
        '사무관리보수',
        '보수차감후수익률',
        '설정액',
        '설정일',
        '상장좌수',
        '운용규모',
        'KOSPI',
        'KOSDAQ',
        'KONEX',
        '코스피200',
        '중형주',
        '소형주',
        '대형주',
        '가치주',
        '성장주',
        '배당주',
        '방어주',
        '경기민감주',
        '테마주',
        '섹터',
        '업종',
        '지수편입',
        '지수편출',
        '포트폴리오',
        '벤치마크',
        '초과수익',
        '절대수익',
        '상대수익',
        '연환산수익률'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN 'CAGR' THEN 'It is an average annual growth rate calculated assuming that investment, sales, etc. have grown consistently every year over several periods.'
        WHEN '변동성' THEN 'It refers to how much the rate of return fluctuates around the average.'
        WHEN '연환산변동성' THEN 'It is a value that converts the volatility over a certain period of time into one year.'
        WHEN 'MDD' THEN 'This is the maximum amount or percentage of decline in the value of an asset or portfolio compared to its peak.'
        WHEN '샤프비율' THEN 'It is an indicator that shows how much excess return there is per unit of risk.'
        WHEN '소르티노비율' THEN 'It is an indicator of risk compared to profit, reflecting only downside risks.'
        WHEN '정보비율' THEN 'It is an indicator of excess return compared to the benchmark divided by tracking error.'
        WHEN '승률' THEN 'This is the percentage of transactions that ended in profit out of all transactions.'
        WHEN '손익비' THEN 'It refers to the ratio of average profit and average loss.'
        WHEN '리밸런싱주기' THEN 'This is the interval to re-adjust the portfolio proportion.'
        WHEN '팩터' THEN 'It is a common characteristic that explains differences in the returns of various stocks.'
        WHEN '모멘텀' THEN 'It is a concept that utilizes the recent upward or downward trend.'
        WHEN '퀄리티' THEN 'It refers to characteristics that reflect the quality of a company, such as profitability and financial soundness.'
        WHEN '저변동성' THEN 'It refers to a group of stocks or strategy characteristics with small price fluctuations.'
        WHEN '백테스트' THEN 'It is a simulation that verifies strategies using past data.'
        WHEN '워크포워드' THEN 'It is a strategy testing method that divides sections and verifies them sequentially.'
        WHEN '과최적화' THEN 'This is a phenomenon in which future performance deteriorates by focusing too much on past data.'
        WHEN '생존편향' THEN 'It is a mistake to look only at stocks that have survived and mistake past performance for good.'
        WHEN '룩어헤드바이어스' THEN 'This is an error that mixes future information with past judgments.'
        WHEN '거래비용' THEN 'These are costs incurred during the transaction process, such as commissions and taxes.'
        WHEN '슬리피지' THEN 'This is a loss that arises from the difference between the expected transaction price and the actual transaction price.'
        WHEN '법인세차감전순이익' THEN 'This is the net profit before corporate tax is reflected.'
        WHEN '영업외수익' THEN 'This is an item of income generated from activities other than the main business.'
        WHEN '영업외비용' THEN 'This is an expense item incurred from activities other than the main job.'
        WHEN '영업외손익' THEN 'It is the profit or loss that combines non-operating income and non-operating expenses.'
        WHEN '금융수익' THEN 'It is profit generated from financial activities, such as interest, dividends, and valuation gains.'
        WHEN '금융비용' THEN 'These are costs incurred from financial activities, such as interest costs and valuation losses.'
        WHEN '법인세비용' THEN 'This is the cost related to corporate tax incurred during the accounting period.'
        WHEN '이연법인세' THEN 'This refers to the corporate tax effect that will be reflected in the future as a temporary difference.'
        WHEN '이연법인세자산' THEN 'This is a deferred corporate tax item that can result in tax savings in the future.'
        WHEN '이연법인세부채' THEN 'This is a deferred corporate tax item that reflects the effect of increased tax burden in the future.'
        WHEN '지배주주순이익' THEN 'It is the net profit attributable to the owners of the controlling company.'
        WHEN '비지배지분' THEN 'Among the shares of subsidiaries, it is the share of shareholders, not the parent company.'
        WHEN '비지배주주지분' THEN 'This is the capital attributable to non-controlling shareholders in consolidated financial statements.'
        WHEN '포괄손익' THEN 'It is a performance concept that combines current profit and loss and other comprehensive income.'
        WHEN '기타포괄손익' THEN 'It is a comprehensive income item that is not directly reflected in current profit or loss.'
        WHEN '매출채권' THEN 'This is money that has not yet been received after selling a product or service.'
        WHEN '매입채무' THEN 'This is money that has not yet been paid for purchasing raw materials or products.'
        WHEN '재고자산' THEN 'These are products and raw materials held for sale or production.'
        WHEN '유형자산' THEN 'It is a tangible long-term asset like land, buildings, and equipment.'
        WHEN '무형자산' THEN 'Like patented software, it is an intangible long-term asset.'
        WHEN '투자부동산' THEN 'This is real estate held for rental income or capital gains.'
        WHEN '금융자산' THEN 'It is an asset created from a financial contract, such as cash, equity securities, and bonds.'
        WHEN '금융부채' THEN 'It is a liability that arises from financial contracts, such as borrowings and corporate bonds.'
        WHEN '사용권자산' THEN 'This is an item that recognizes the right to use it as an asset through a lease contract.'
        WHEN '리스부채' THEN 'This is an item in which the amount to be paid in the future under a lease contract is recognized as a liability.'
        WHEN '단기차입금' THEN 'It is a loan that must be repaid within one year.'
        WHEN '장기차입금' THEN 'This is a loan with a repayment period of more than one year.'
        WHEN '유동성장기부채' THEN 'Although it was originally a long-term debt, it is a debt classified as repayable within one year.'
        WHEN '관계기업' THEN 'This is a company subject to investment that has significant influence to the extent that the equity method can be applied.'
        WHEN '종속기업' THEN 'It is a company that has control and is included in the consolidated financial statements.'
        WHEN '관계기업투자' THEN 'It is an investment asset generated through holding shares in an affiliated company.'
        WHEN '종속기업투자' THEN 'It is an investment asset generated through ownership of shares in a subsidiary.'
        WHEN '지분법' THEN 'It is an accounting method that evaluates investment value by reflecting changes in the investment company''s net assets.'
        WHEN '지분법이익' THEN 'This is the share of profits recognized from investments using the equity method.'
        WHEN '지분법손실' THEN 'This is the share of losses recognized in investments using the equity method.'
        WHEN '감가상각비' THEN 'This is a cost in which the value of tangible assets is distributed according to the period of use.'
        WHEN '상각비' THEN 'This is the cost of distributing the value of an intangible asset according to its period of use.'
        WHEN '충당부채' THEN 'Although the timing or amount of expenditure is uncertain, it is a liability with a high probability of occurrence.'
        WHEN '충당금' THEN 'This is an amount set in advance to prepare for future losses or costs.'
        WHEN '대손충당금' THEN 'This is a provision for losses on receivables that may not be received.'
        WHEN '운전자본' THEN 'It is short-term operating funds obtained by subtracting current liabilities from current assets.'
        WHEN '순운전자본' THEN 'This is the amount of net liquid funds actually tied up in operating activities.'
        WHEN '연구개발비' THEN 'This is the cost invested in developing new technologies and new products.'
        WHEN '설비투자' THEN 'This is an investment spent on expanding production facilities and infrastructure.'
        WHEN '매출채권회수기간' THEN 'This is the average time it takes to collect trade receivables in cash.'
        WHEN '재고자산회전기간' THEN 'This is the average time it takes to sell inventory and convert it into funds.'
        WHEN '매입채무회전기간' THEN 'This is the average time it takes to pay accounts payable.'
        WHEN '현금전환주기' THEN 'This is the period in which money is tied up, from purchasing inventory to collecting accounts receivable.'
        WHEN 'ROIC' THEN 'It is a profitability indicator that looks at operating performance compared to invested capital.'
        WHEN 'NOPAT' THEN 'It is a profit indicator for analysis, which means operating profit after tax.'
        WHEN 'EBITDA마진' THEN 'Cash generation is measured by the ratio of EBITDA to sales.'
        WHEN '영업레버리지' THEN 'It shows how significantly changes in sales are reflected in operating profit.'
        WHEN '재무레버리지' THEN 'This refers to the effect that the use of debt has on the return on equity.'
        WHEN '권리락' THEN 'This is a phenomenon in which stock prices are adjusted after the right to allocate new shares disappears.'
        WHEN '배당락' THEN 'This is a phenomenon in which stock prices are adjusted after dividend rights disappear.'
        WHEN '기준일' THEN 'This is the date used as a standard for determining rights, such as confirming the shareholder list.'
        WHEN '신주배정기준일' THEN 'This is the date to decide which shareholders will be allocated new shares in paid-in capital increase, etc.'
        WHEN '주주제안' THEN 'It is the right for shareholders who meet certain requirements to propose agenda items for a general meeting of shareholders.'
        WHEN '집중투표제' THEN 'It is a system that allows voting rights to be assigned to one candidate when electing a director.'
        WHEN '서면투표' THEN 'This is a method of exercising voting rights in writing rather than attending a general shareholders'' meeting in person.'
        WHEN '주주명부폐쇄' THEN 'This is a procedure to prevent changes to the shareholder list for a certain period of time to confirm rights.'
        WHEN '주요주주' THEN 'Shareholders who can have a significant impact on company management and governance.'
        WHEN '실적공시' THEN 'A company announces its performance over a certain period of time through public announcements or announcements.'
        WHEN '잠정실적' THEN 'These are performance figures announced first in the pre-confirmation stage.'
        WHEN '투자설명서' THEN 'It is an explanatory document containing the information necessary to make investment decisions in securities or products.'
        WHEN '예비투자설명서' THEN 'It is a preliminary explanation document provided at the stage before full-scale subscription.'
        WHEN '정정신고서' THEN 'This is a document being resubmitted due to changes in the contents of the existing report.'
        WHEN '공시서류' THEN 'These are various public disclosure documents submitted in accordance with laws or exchange regulations.'
        WHEN '소액공모' THEN 'This is a small-scale securities offering for which simple procedures can be applied.'
        WHEN '임원선임' THEN 'This is the process of electing or electing new company executives.'
        WHEN '감사선임' THEN 'This is the procedure for electing auditors or audit committee members.'
        WHEN '파생상품' THEN 'It is a financial product whose value is determined based on the price of the underlying asset.'
        WHEN '기초자산' THEN 'It is an asset or indicator that serves as the standard for calculating the value of derivative products.'
        WHEN '선도계약' THEN 'It is an over-the-counter contract to trade an asset at a specific point in the future.'
        WHEN '선물가격' THEN 'This is the price at which the futures contract is currently trading in the market.'
        WHEN '선물만기' THEN 'This is the point at which the futures contract is terminated and settlement is made.'
        WHEN '베이시스' THEN 'It refers to the difference between spot price and futures price.'
        WHEN '콘탱고' THEN 'This means that the futures price is higher than the spot price.'
        WHEN '백워데이션' THEN 'This means that the futures price is lower than the spot price.'
        WHEN '옵션프리미엄' THEN 'This is the price paid to purchase option rights.'
        WHEN '내재변동성' THEN 'It is the expectation of future volatility reflected in the option price.'
        WHEN '역사적변동성' THEN 'This is the actual volatility calculated based on past price data.'
        WHEN '델타' THEN 'Option price sensitivity to changes in the price of the underlying asset.'
        WHEN '감마' THEN 'Option sensitivity shows how quickly delta changes.'
        WHEN '세타' THEN 'It shows the degree of decrease in option value over time.'
        WHEN '베가' THEN 'Option price sensitivity to changes in volatility.'
        WHEN '마진콜' THEN 'This is a measure to request additional margin when the margin is insufficient.'
        WHEN '일일정산' THEN 'This is a procedure to calculate and reflect futures and option profits and losses on a daily basis.'
        WHEN '청산' THEN 'This is the process of closing a position or finalizing payment obligations.'
        WHEN '만기청산' THEN 'At the time of maturity, the position is liquidated and settlement is completed.'
        WHEN '현금결제' THEN 'Instead of physical delivery, only the difference is settled in cash.'
        WHEN '실물인수도' THEN 'Payment is made by handing over and receiving actual assets.'
        WHEN '미결제약정' THEN 'This is the contract quantity for which counter-sale or settlement has not yet been completed.'
        WHEN '스프레드거래' THEN 'It is a transaction that utilizes the difference in price of two or more stocks or maturity.'
        WHEN '캘린더스프레드' THEN 'It is a transaction that takes advantage of the differences in contracts with different maturity dates for the same asset.'
        WHEN '헤지' THEN 'This is to take a position in the opposite direction to reduce the risk of price fluctuations.'
        WHEN '헤지비율' THEN 'It is the ratio of hedging size required to reduce risk exposure.'
        WHEN '변동성지수' THEN 'This is a value that expresses market volatility expectations in the form of an index.'
        WHEN '콜매도' THEN 'This is a position that earns premium profits by selling call options.'
        WHEN '풋매수' THEN 'This is a position to buy put options in preparation for a decline in the underlying asset.'
        WHEN 'YTM' THEN 'This refers to the expected yield to maturity when a bond is held until maturity.'
        WHEN 'AUM' THEN 'This is the total amount of assets that the management company is managing with investor funds.'
        WHEN '성과보수' THEN 'This is additional compensation received when management performance exceeds a certain standard.'
        WHEN '환매지연' THEN 'Payment of the redemption price is delayed depending on market conditions or regulations.'
        WHEN '클래스펀드' THEN 'Within the same fund, there are sales units with different compensation systems.'
        WHEN '목표전환형펀드' THEN 'It is a fund that changes its asset composition when the target return is reached.'
        WHEN 'ETF분배금' THEN 'This is the amount that the ETF distributes to investors based on the profits from the assets it holds.'
        WHEN '리스크프리레이트' THEN 'It is the standard rate of return expected from a risk-free asset.'
        WHEN '알파' THEN 'It refers to additional performance that exceeds the market or benchmark.'
        WHEN '베타' THEN 'This coefficient indicates sensitivity to market movements.'
        WHEN '공분산' THEN 'It shows the degree to which the returns on two assets move together.'
        WHEN '상관계수' THEN 'It is a statistical indicator that indicates the direction and strength of two variables.'
        WHEN '팩터노출' THEN 'It refers to how sensitive a portfolio is to changes in a specific factor.'
        WHEN '벤치마크수익률' THEN 'It is the rate of return of the index or portfolio that serves as a standard for comparison.'
        WHEN '누적수익률' THEN 'This is the total return accumulated from the base point to the present.'
        WHEN '일별수익률' THEN 'It is a rate of return calculated from daily price changes.'
        WHEN '월별수익률' THEN 'It is a rate of return calculated based on monthly performance.'
        WHEN '거래회전율' THEN 'This refers to how often assets or portfolios are replaced over a certain period of time.'
        WHEN '시나리오분석' THEN 'This is an analysis that compares the results of various assumed market situations.'
        WHEN '민감도분석' THEN 'This is an analysis that looks at how much a change in a specific variable affects the results.'
        WHEN '스트레스테스트' THEN 'This is a test that checks the possibility of loss assuming extreme market conditions.'
        WHEN '리스크관리' THEN 'It is the overall process of measuring and controlling the possibility of loss.'
        WHEN '검증구간' THEN 'This is a section set aside to check model or strategy performance.'
        WHEN '학습구간' THEN 'This is the section used to match model rules or parameters.'
        WHEN '테스트구간' THEN 'This is a separate verification section to check the final performance.'
        WHEN '인샘플' THEN 'This is the data section used for model design and adjustment.'
        WHEN '아웃오브샘플' THEN 'This is a data section that is not used for model adjustment but is verified separately.'
        WHEN '홀드아웃' THEN 'This is a data section excluded from learning and used only for final verification.'
        WHEN '몽테카를로시뮬레이션' THEN 'This is a method of estimating the result distribution through stochastic repeated experiments.'
        WHEN '회복기간' THEN 'This is the time it takes to recover the previous high point after a fall.'
        WHEN 'Forward PER' THEN 'PER is calculated based on expected net profit and reflects future profit prospects.'
        WHEN 'Trailing PER' THEN 'We look at the stock price level compared to past profits with PER calculated based on recently confirmed performance.'
        WHEN 'Forward PBR' THEN 'PBR calculated based on expected net assets reflects future capital changes.'
        WHEN 'EV/Sales' THEN 'Corporate value is divided by sales and is used to compare companies with high profit volatility.'
        WHEN 'EV/EBIT' THEN 'The corporate value is divided by operating profit, and the difference in borrowing structure is corrected for comparison.'
        WHEN 'EV/FCF' THEN 'The value of the company is divided by the free cash flow to determine the value relative to cash generation.'
        WHEN 'EV/Revenue' THEN 'It is used to compare multiple growth companies with corporate value divided by sales.'
        WHEN 'Price/Cash Flow' THEN 'Comparing the stock price to the cash flow per share looks at cash generation rather than accounting profit.'
        WHEN 'PCFR' THEN 'Compare the stock price to the operating cash flow per share to look at the price relative to cash generation.'
        WHEN 'PER 밴드' THEN 'It is a framework to use the past PER range to see what range the current stock price is.'
        WHEN 'PBR 밴드' THEN 'It is a framework that compares the stock price position relative to asset value using the past PBR range.'
        WHEN '멀티플' THEN 'It is a valuation multiple that indicates how many times the price is compared to a standard value such as profits or assets.'
        WHEN '상대가치평가' THEN 'This is a method of calculating corporate value by comparing it to similar companies or past averages.'
        WHEN '절대가치평가' THEN 'This is a method of estimating the value of the company itself by discounting future cash flows or dividends.'
        WHEN 'DCF' THEN 'This is an valuation method that calculates the current corporate value by discounting future cash flows.'
        WHEN 'DDM' THEN 'It is a dividend discount model that calculates stock value by discounting expected dividends.'
        WHEN 'RIM' THEN 'This is a method of estimating stock value using residual profits that exceed the cost of capital.'
        WHEN 'WACC' THEN 'A discount rate that is a weighted average of the cost of debt and equity capital is used to evaluate corporate value.'
        WHEN 'COE' THEN 'It is the cost of equity capital required by shareholders and is a key input to the stock value discount rate.'
        WHEN 'CAPM' THEN 'This is a model that estimates the expected rate of return using the risk-free rate of return and the market risk premium.'
        WHEN '언레버드베타' THEN 'Beta is used to compare business risks with the effect of financial leverage removed.'
        WHEN '무위험수익률' THEN 'The rate of return on assets with little risk is used as a reference point for calculating the discount rate.'
        WHEN '시장위험프리미엄' THEN 'This is the compensation that the market portfolio requires in addition to the risk-free rate of return.'
        WHEN '영구성장률' THEN 'This is the ratio at which cash flow is assumed to grow in the long term after the explicit estimation period.'
        WHEN '터미널가치' THEN 'This is an amount that reflects the value that the business will create after the estimation period at the present time.'
        WHEN '할인율' THEN 'This is the rate of return or cost rate applied when converting future amount to present value.'
        WHEN '적정주가' THEN 'This is the reasonable price level of the stock estimated based on a valuation model or comparison standard.'
        WHEN '목표주가' THEN 'It is a stock price that analysts or models suggest can be reached after a certain period of time.'
        WHEN '업사이드' THEN 'It refers to the upside potential from the current price to the target or appropriate price.'
        WHEN '다운사이드' THEN 'It refers to the downside potential for possible losses compared to the current price.'
        WHEN '안전마진' THEN 'It is a conservative margin secured between the estimated value and the purchase price.'
        WHEN '내재가치' THEN 'It is an estimated value based on the intrinsic cash-generating ability of a company or asset.'
        WHEN '청산가치' THEN 'It is the value that is expected to remain after the company''s assets are disposed of and debts are paid off.'
        WHEN '장부가치' THEN 'It is the accounting amount of assets or capital recorded in financial statements.'
        WHEN '순자산가치평가' THEN 'This is a method of calculating corporate value based on net assets minus liabilities.'
        WHEN 'SOTP' THEN 'This is a method of calculating the overall corporate value by evaluating the value of each business unit separately and then adding them together.'
        WHEN '합산가치평가' THEN 'This is a method of estimating the overall value by combining the individual values ​​of several business units or assets.'
        WHEN '프리미엄' THEN 'This refers to a state where a price or valuation multiple is higher than the comparison standard.'
        WHEN '할인' THEN 'This refers to the state of being traded at a price or valuation multiple that is lower than the comparison standard.'
        WHEN '지주회사할인' THEN 'This is a phenomenon in which the stock price of a holding company is evaluated lower than the total value of its subsidiaries.'
        WHEN '지배주주할인' THEN 'It is a value discount that reflects the fact that minority shareholders cannot exercise control.'
        WHEN '유동성할인' THEN 'This is a value discount applied to assets that are difficult to trade or take a long time to sell.'
        WHEN '비상장주식평가' THEN 'This is a process of estimating the value of stocks without a market price using financial information and comparative examples.'
        WHEN '희석가치' THEN 'This is the value per share adjusted to reflect the possibility of issuing new shares or convertible securities.'
        WHEN '희석EPS' THEN 'This is earnings per share calculated assuming that all potential stocks are converted to common stocks.'
        WHEN '완전희석주식수' THEN 'This is the number of potential common stocks that reflects all conversion rights and stock compensation.'
        WHEN '워런트희석' THEN 'This has the effect of lowering the value per share as the number of shares increases due to the exercise of new stock warrants.'
        WHEN '전환희석' THEN 'This is dilution in the value per share that occurs when convertible bonds or convertible preferred stock are converted to common stock.'
        WHEN '밸류에이션리레이팅' THEN 'It refers to a re-evaluation phase in which the valuation multiple is higher than before.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        'CAGR',
        '변동성',
        '연환산변동성',
        'MDD',
        '샤프비율',
        '소르티노비율',
        '정보비율',
        '승률',
        '손익비',
        '리밸런싱주기',
        '팩터',
        '모멘텀',
        '퀄리티',
        '저변동성',
        '백테스트',
        '워크포워드',
        '과최적화',
        '생존편향',
        '룩어헤드바이어스',
        '거래비용',
        '슬리피지',
        '법인세차감전순이익',
        '영업외수익',
        '영업외비용',
        '영업외손익',
        '금융수익',
        '금융비용',
        '법인세비용',
        '이연법인세',
        '이연법인세자산',
        '이연법인세부채',
        '지배주주순이익',
        '비지배지분',
        '비지배주주지분',
        '포괄손익',
        '기타포괄손익',
        '매출채권',
        '매입채무',
        '재고자산',
        '유형자산',
        '무형자산',
        '투자부동산',
        '금융자산',
        '금융부채',
        '사용권자산',
        '리스부채',
        '단기차입금',
        '장기차입금',
        '유동성장기부채',
        '관계기업',
        '종속기업',
        '관계기업투자',
        '종속기업투자',
        '지분법',
        '지분법이익',
        '지분법손실',
        '감가상각비',
        '상각비',
        '충당부채',
        '충당금',
        '대손충당금',
        '운전자본',
        '순운전자본',
        '연구개발비',
        '설비투자',
        '매출채권회수기간',
        '재고자산회전기간',
        '매입채무회전기간',
        '현금전환주기',
        'ROIC',
        'NOPAT',
        'EBITDA마진',
        '영업레버리지',
        '재무레버리지',
        '권리락',
        '배당락',
        '기준일',
        '신주배정기준일',
        '주주제안',
        '집중투표제',
        '서면투표',
        '주주명부폐쇄',
        '주요주주',
        '실적공시',
        '잠정실적',
        '투자설명서',
        '예비투자설명서',
        '정정신고서',
        '공시서류',
        '소액공모',
        '임원선임',
        '감사선임',
        '파생상품',
        '기초자산',
        '선도계약',
        '선물가격',
        '선물만기',
        '베이시스',
        '콘탱고',
        '백워데이션',
        '옵션프리미엄',
        '내재변동성',
        '역사적변동성',
        '델타',
        '감마',
        '세타',
        '베가',
        '마진콜',
        '일일정산',
        '청산',
        '만기청산',
        '현금결제',
        '실물인수도',
        '미결제약정',
        '스프레드거래',
        '캘린더스프레드',
        '헤지',
        '헤지비율',
        '변동성지수',
        '콜매도',
        '풋매수',
        'YTM',
        'AUM',
        '성과보수',
        '환매지연',
        '클래스펀드',
        '목표전환형펀드',
        'ETF분배금',
        '리스크프리레이트',
        '알파',
        '베타',
        '공분산',
        '상관계수',
        '팩터노출',
        '벤치마크수익률',
        '누적수익률',
        '일별수익률',
        '월별수익률',
        '거래회전율',
        '시나리오분석',
        '민감도분석',
        '스트레스테스트',
        '리스크관리',
        '검증구간',
        '학습구간',
        '테스트구간',
        '인샘플',
        '아웃오브샘플',
        '홀드아웃',
        '몽테카를로시뮬레이션',
        '회복기간',
        'Forward PER',
        'Trailing PER',
        'Forward PBR',
        'EV/Sales',
        'EV/EBIT',
        'EV/FCF',
        'EV/Revenue',
        'Price/Cash Flow',
        'PCFR',
        'PER 밴드',
        'PBR 밴드',
        '멀티플',
        '상대가치평가',
        '절대가치평가',
        'DCF',
        'DDM',
        'RIM',
        'WACC',
        'COE',
        'CAPM',
        '언레버드베타',
        '무위험수익률',
        '시장위험프리미엄',
        '영구성장률',
        '터미널가치',
        '할인율',
        '적정주가',
        '목표주가',
        '업사이드',
        '다운사이드',
        '안전마진',
        '내재가치',
        '청산가치',
        '장부가치',
        '순자산가치평가',
        'SOTP',
        '합산가치평가',
        '프리미엄',
        '할인',
        '지주회사할인',
        '지배주주할인',
        '유동성할인',
        '비상장주식평가',
        '희석가치',
        '희석EPS',
        '완전희석주식수',
        '워런트희석',
        '전환희석',
        '밸류에이션리레이팅'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '디레이팅' THEN 'This refers to a phase in which the valuation multiple is lowered due to changes in profit or risk perception.'
        WHEN '피어밸류에이션' THEN 'It is a method of comparing value with companies with similar businesses and financial structures.'
        WHEN '섹터멀티플' THEN 'It is a representative valuation multiple for companies in the same industry and is used to compare individual companies.'
        WHEN '이익수익률' THEN 'It is the ratio of earnings per share divided by stock price and is the reciprocal of PER.'
        WHEN 'FCF수익률' THEN 'It is a rate of return indicator that compares free cash flow to corporate value or market capitalization.'
        WHEN '주주환원수익률' THEN 'We look at the level of return compared to market capitalization by combining dividends and share buybacks.'
        WHEN 'TSR' THEN 'It refers to the total shareholder return that combines stock price increases and dividends.'
        WHEN 'ROE-PBR 모델' THEN 'It is an evaluation framework that explains the appropriate PBR level in terms of the relationship between ROE and cost of capital.'
        WHEN '성장가치' THEN 'This is the portion of corporate value that is expected to be additionally created through future growth.'
        WHEN '자산가치' THEN 'This is the part of the corporate value calculated based on the assessed value of the assets held.'
        WHEN '수익가치' THEN 'It is a value calculated based on the profit or cash flow that a company will earn.'
        WHEN '순현금' THEN 'Financial stability is assessed by subtracting total borrowings from cash equivalents.'
        WHEN '영업수익' THEN 'It is the profit generated from a company''s main business activities and is used similarly to sales in the financial industry.'
        WHEN '영업비용' THEN 'It refers to the costs incurred to carry out main business activities.'
        WHEN '판매비와관리비' THEN 'This is the formal expression for SG&A expenses, which are the costs incurred in selling products and managing the company.'
        WHEN '금융원가' THEN 'These are finance-related costs, such as interest costs arising from borrowings or financial liabilities.'
        WHEN '기타수익' THEN 'It is a revenue item with low repeatability generated outside of main business activities.'
        WHEN '기타비용' THEN 'This is an expense item incurred outside of main business activities.'
        WHEN '계속영업손익' THEN 'It is profit or loss arising from a business segment that continues without interruption.'
        WHEN '중단영업손익' THEN 'This is the profit or loss arising from a business division that has been decided to sell or close.'
        WHEN '기본주당이익' THEN 'It is the profit attributable to common stocks divided by the weighted average number of common stocks.'
        WHEN '희석주당이익' THEN 'This is earnings per share calculated by reflecting the dilution effect of potential common stock.'
        WHEN '가중평균유통주식수' THEN 'It is a quantity calculated by weighting the number of common shares in circulation during the period by the holding period.'
        WHEN '보통주자본' THEN 'It refers to capital items formed in connection with the issuance of common stocks.'
        WHEN '자본금' THEN 'This is the legal capital amount paid to the company by issuing shares.'
        WHEN '자본잉여금' THEN 'It refers to the surplus generated from capital transactions, such as stock issuance premium.'
        WHEN '이익잉여금' THEN 'This is the amount of profits earned and left to the company without being distributed.'
        WHEN '기타자본구성요소' THEN 'In addition to capital and surplus, these are other items classified as capital.'
        WHEN '자본조정' THEN 'It is an item that is deducted or adjusted from capital, like treasury stocks.'
        WHEN '기타포괄손익누계액' THEN 'It is the accumulated amount of other comprehensive income that is not immediately reflected in the income statement.'
        WHEN '비유동금융자산' THEN 'It is a financial product or investment asset that will be held for more than one year.'
        WHEN '유동금융자산' THEN 'It is a financial product or investment asset that is expected to be converted into cash within one year.'
        WHEN '기타채권' THEN 'It is a monetary receivable that one has the right to receive in addition to trade receivables.'
        WHEN '기타채무' THEN 'It is a monetary debt that must be paid in addition to trade payables.'
        WHEN '선수금' THEN 'This is the amount received before goods or services are provided.'
        WHEN '선급금' THEN 'This is the amount paid before receiving goods or services.'
        WHEN '미수금' THEN 'This is an amount that has not yet been received from non-business transactions.'
        WHEN '미지급금' THEN 'This is an amount that has not yet been paid in non-business transactions.'
        WHEN '미수수익' THEN 'Although the period has passed and profits have been generated, it is an amount that has not yet been received.'
        WHEN '미지급비용' THEN 'Expenses have been incurred over time, but have not yet been paid.'
        WHEN '계약자산' THEN 'It is an asset that has fulfilled performance obligations but has not yet become an unconditional claim.'
        WHEN '계약부채' THEN 'It is a debt paid for before providing goods or services to the customer.'
        WHEN '충당부채전입액' THEN 'This is the amount recognized as an additional provision in preparation for possible future expenditures.'
        WHEN '퇴직급여부채' THEN 'It is a liability measured by the present value of the obligation to pay employee retirement benefits.'
        WHEN '확정급여채무' THEN 'This is the retirement benefit obligation borne by a company to its employees in a defined benefit system.'
        WHEN '사외적립자산' THEN 'These are assets accumulated outside the company to pay retirement benefits.'
        WHEN '재측정요소' THEN 'It is a factor arising from changes in the valuation of defined benefit obligations or plan assets.'
        WHEN '영업권' THEN 'The amount by which the acquisition consideration exceeds the fair value of identifiable net assets.'
        WHEN '손상차손' THEN 'It is a loss recognized when the recoverable amount of an asset is lower than its carrying amount.'
        WHEN '손상차손환입' THEN 'This is the amount that reduces previously recognized impairment losses and returns them to profit.'
        WHEN '재평가잉여금' THEN 'This is the amount recognized in equity when the book value increases due to asset revaluation.'
        WHEN '감가상각누계액' THEN 'It is the total depreciation expense accumulated after the acquisition of tangible assets.'
        WHEN '매출채권손상' THEN 'This is an accounting treatment that reflects losses when it is difficult to collect accounts receivable.'
        WHEN '대손상각비' THEN 'This is the amount recognized as an expense for the possibility of not being able to collect the receivable.'
        WHEN '현금흐름표' THEN 'It is a financial statement that shows cash inflow and outflow by operating, investment and financial activities.'
        WHEN '재무상태표' THEN 'It is a financial statement that shows the status of assets, liabilities, and capital at a specific point in time.'
        WHEN '손익계산서' THEN 'It is a financial statement that shows revenues, costs and profits over a certain period of time.'
        WHEN '포괄손익계산서' THEN 'It is a financial statement that shows both current profit and loss and other comprehensive income.'
        WHEN '자본변동표' THEN 'It is a financial statement that shows the reasons for increases or decreases in capital items by period.'
        WHEN '주석' THEN 'Detailed explanations and accounting policies needed to understand financial statement numbers.'
        WHEN '감사의견' THEN 'This is the opinion that the auditor presents regarding the adequacy of financial statements.'
        WHEN '계속기업' THEN 'The accounting premise is that a company will continue to operate for a predictable period of time.'
        WHEN '유동성분류' THEN 'It is a standard for dividing assets and liabilities into current and non-current depending on the settlement or recovery period.'
        WHEN '공정가치' THEN 'It is the price at which assets can be sold or liabilities transferred in normal transactions between market participants.'
        WHEN '상각후원가' THEN 'It is a value that measures the book value of a financial asset or liability by applying the effective interest rate method.'
        WHEN '유효이자율' THEN 'It is a discount rate that matches the expected cash flow of a financial product with its book value.'
        WHEN '금융보증부채' THEN 'This is the payment obligation borne by the guarantor when the debtor fails to pay.'
        WHEN '우발부채' THEN 'It is a potential obligation that is confirmed depending on the occurrence of a future event.'
        WHEN '우발자산' THEN 'It is a potential asset that is confirmed depending on whether a future event occurs or not.'
        WHEN '영업권손상' THEN 'This is a situation in which a loss is recognized because the recoverable amount of goodwill is lower than the book value.'
        WHEN '재고평가손실' THEN 'This is a loss recognized when the net realizable value of inventory is lower than its cost.'
        WHEN '원가율' THEN 'It is viewed together with gross profit margin as the ratio of cost of goods sold to sales.'
        WHEN '고정비' THEN 'It is a cost that does not change significantly in the short term even if production or sales change.'
        WHEN '변동비' THEN 'It is a cost that changes with changes in production or sales.'
        WHEN '손익분기점' THEN 'This is the level of sales at which total revenue and total cost are equal and profit is zero.'
        WHEN '공헌이익' THEN 'It is the amount calculated by subtracting variable costs from sales and contributes to recovering fixed costs and generating profits.'
        WHEN '운전자본회전율' THEN 'It is an indicator of the efficiency of working capital utilization by comparing sales with working capital.'
        WHEN '경기순환' THEN 'It refers to the flow of economic activity that repeats expansion and contraction.'
        WHEN '확장국면' THEN 'This is an economic phase in which economic activities, including production, consumption, and employment, are increasing overall.'
        WHEN '수축국면' THEN 'This is an economic phase in which economic activities such as production, consumption, and employment are decreasing overall.'
        WHEN '경기저점' THEN 'It refers to the point where economic contraction ends and transitions into expansion.'
        WHEN '경기정점' THEN 'It refers to the point where economic expansion ends and transitions into contraction.'
        WHEN '산출갭' THEN 'It is the difference that indicates how much the actual output deviates from the potential output.'
        WHEN 'GDP갭' THEN 'It is an indicator of economic pressure expressed as a ratio of the difference between actual GDP and potential GDP.'
        WHEN '필립스곡선' THEN 'It is a macroeconomic model that explains the relationship between inflation rate and unemployment rate.'
        WHEN '자연실업률' THEN 'Even excluding economic fluctuation factors, this is the level of unemployment that exists in the economic structure.'
        WHEN 'NAIRU' THEN 'It is a macroeconomic concept that refers to the rate of unemployment that does not accelerate the rate of inflation.'
        WHEN '노동생산성' THEN 'It is the output or added value created by one unit of labor input.'
        WHEN 'TFP' THEN 'It is the increase in production efficiency that is not explained by labor and capital input.'
        WHEN '잠재GDP' THEN 'This is the GDP that the economy can sustainably produce without significantly increasing price pressure.'
        WHEN '총고정자본형성' THEN 'This refers to investment expenditures on fixed assets such as construction equipment and intellectual property.'
        WHEN '민간소비' THEN 'This is the spending on goods and services by households and private non-profit organizations.'
        WHEN '정부소비' THEN 'It is consumer expenditures made by the government to provide public services.'
        WHEN '민간설비투자' THEN 'This is the amount invested by the private sector in machinery and equipment to expand production capacity.'
        WHEN '건설투자' THEN 'This is investment expenditure on construction assets such as buildings and civil engineering facilities.'
        WHEN '지식재산생산물투자' THEN 'It is an investment in intellectual property products such as research and development software.'
        WHEN '수출' THEN 'It is a transaction in which a resident sells goods or services to a non-resident.'
        WHEN '수입' THEN 'This is a transaction in which a resident purchases goods or services from a non-resident.'
        WHEN '순수출' THEN 'It is calculated by subtracting imports from exports and is a component of GDP expenditure.'
        WHEN '교역조건' THEN 'It shows external purchasing power by the relative ratio between export price and import price.'
        WHEN '경상거래' THEN 'It is a concept that encompasses repetitive international transactions such as goods, services, and income transfers.'
        WHEN '자본수지' THEN 'It is a balance of payments item that records acquisition of non-produced non-financial assets and capital transfer transactions.'
        WHEN '직접투자' THEN 'This is a transaction of investing in an overseas company for the purpose of participating in management or forming a long-term relationship.'
        WHEN '증권투자' THEN 'It is an international financial transaction that involves investing in marketable securities such as stocks and bonds.'
        WHEN '기타투자' THEN 'It is a financial transaction other than direct investment and securities investment, such as loans, trade credits, and deposits.'
        WHEN '준비자산' THEN 'It is a foreign currency asset held by the central bank for external payments and stabilization of the foreign exchange market.'
        WHEN '대외채무' THEN 'It is an external debt owed by a resident to a non-resident.'
        WHEN '대외채권' THEN 'It is an external asset that a resident has the right to receive from a non-resident.'
        WHEN '순대외채권' THEN 'It is an indicator of net assets obtained by subtracting external debt from external receivables.'
        WHEN 'CDS프리미엄' THEN 'It is a spread in the form of an insurance premium paid in a CDS contract that transfers default risk.'
        WHEN '국가신용등급' THEN 'It is a rating that evaluates a country''s ability and will to repay its debt.'
        WHEN '재정수지' THEN 'It shows the fiscal surplus or deficit as the difference between government income and expenditure.'
        WHEN '관리재정수지' THEN 'It is a complementary indicator of the government''s financial situation, excluding social security funds, etc.'
        WHEN '통합재정수지' THEN 'It is a fiscal balance calculated by integrating the income and expenditure of the central government and the fund.'
        WHEN '국가채무' THEN 'It is the total amount of debt that the government is obligated to repay.'
        WHEN '조세부담률' THEN 'It is the ratio of tax revenue to gross domestic product.'
        WHEN '소비성향' THEN 'It refers to the proportion of income spent on consumption.'
        WHEN '저축률' THEN 'It is the percentage of income left for savings rather than consumption.'
        WHEN '가계부채' THEN 'This is debt that includes loans and sales credit borrowed by households from financial institutions.'
        WHEN '가계신용' THEN 'It is an indicator of household credit size, which combines household loans and sales credit.'
        WHEN '기업신용' THEN 'It refers to the amount of credit, including loans and bonds, supplied to the corporate sector.'
        WHEN '신용사이클' THEN 'It is a financial cycle in which credit supply and borrowing expand and contract repeatedly.'
        WHEN '금융순환' THEN 'It is a long-term cycle in which credit, real estate prices, and financial conditions move together.'
        WHEN '통화정책파급경로' THEN 'This is the process by which central bank policy is transmitted to the real economy through expectations of interest rates, credit, and exchange rates.'
        WHEN '금리경로' THEN 'It is a transmission channel through which policy interest rate changes affect market interest rates and consumption investment.'
        WHEN '신용경로' THEN 'This is the transmission channel through which changes in financial institution lending conditions affect the real economy.'
        WHEN '환율경로' THEN 'This is the path through which changes in monetary policy and financial conditions affect imports, exports, and prices through exchange rates.'
        WHEN '기대경로' THEN 'This is the path through which policy signals change the expectations of economic entities and affect consumption, investment, and prices.'
        WHEN '물가안정목표제' THEN 'It is a system in which the central bank sets an inflation rate target and operates policies to achieve it.'
        WHEN '기준순환일' THEN 'This is the date on which the peak and trough of the game are officially determined and marked.'
        WHEN '생산갭' THEN 'It refers to the difference between actual production and potential production.'
        WHEN '근원물가' THEN 'It is an indicator of basic price trends by excluding items with large temporary fluctuations.'
        WHEN '생활물가지수' THEN 'It is a perceived price index calculated based on items that consumers frequently purchase.'
        WHEN '수입물가지수' THEN 'It is a price index that indexes the price fluctuations of imported products.'
        WHEN '수출물가지수' THEN 'It is a price index that indexes the price fluctuations of export products.'
        WHEN '임금상승률' THEN 'It is the rate of increase in the wage level received by workers.'
        WHEN '단위노동비용' THEN 'It refers to the labor cost required to produce one unit of output.'
        WHEN '명목임금' THEN 'This is the amount of wages without adjustment for price changes.'
        WHEN '실질임금' THEN 'It is a wage based on purchasing power, where nominal wages are adjusted to the price level.'
        WHEN '경제활동참가율' THEN 'It is the ratio of the economically active population, which includes the employed and the unemployed, among the working-age population.'
        WHEN '고용탄력성' THEN 'It indicates how sensitive employment is to changes in economic growth rate.'
        WHEN '장기금리' THEN 'This is the interest rate applied to long-maturity bonds or financial products.'
        WHEN '단기금리' THEN 'This is the interest rate applied to financial transactions or financial products with short maturity.'
        WHEN '금리차' THEN 'The difference between the two interest rates reflects the difference in maturity or credit risk.'
        WHEN 'TED스프레드' THEN 'It is an interest rate spread that looks at inter-bank credit risk and short-term money market instability.'
        WHEN '역레포' THEN 'It is a repurchase agreement transaction in which a central bank or financial institution purchases securities and later sells them back.'
        WHEN '지급결제망' THEN 'It is a computer network where fund transfers and payments are made between financial institutions.'
        WHEN 'RTGS' THEN 'It is a real-time total payment system that immediately processes large payments on a case-by-case basis.'
        WHEN '순액결제' THEN 'This is a final payment method in which only the net amount of multiple payments is offset.'
        WHEN '외환건전성부담금' THEN 'This is a levy levied on foreign currency debt of financial companies to mitigate risks in the foreign exchange sector.'
        WHEN '거시건전성정책' THEN 'This is a policy to suppress the accumulation of risks in the entire financial system.'
        WHEN '정기공시' THEN 'This is a corporate disclosure that is submitted at set intervals, such as business reports.'
        WHEN '수시공시' THEN 'This is a disclosure submitted immediately when an important management event occurs.'
        WHEN '공정공시' THEN 'It is a system that discloses important information to all investors before selectively providing it to specific people.'
        WHEN '자율공시' THEN 'This is a disclosure by a listed company that voluntarily discloses matters that are helpful in making investment decisions.'
        WHEN '조회공시' THEN 'This is a public notice in which the exchange requests a response from the company regarding rumors or reasons for sudden changes in the stock price.'
        WHEN '해명공시' THEN 'This is a public announcement in which the company explains the facts related to rumors or reports.'
        WHEN '풍문또는보도' THEN 'This is information subject to disclosure and confirmation through rumors known to the market or media reports.'
        WHEN '공급계약' THEN 'It refers to a contract where a company agrees to supply goods or services.'
        WHEN '단일판매공급계약' THEN 'It is an important sales or supply contract concluded with a single customer.'
        WHEN '단기차입금증가결정' THEN 'This is a notice to notify when the size of short-term borrowing increases significantly.'
        WHEN '타법인주식취득' THEN 'It refers to a transaction or decision to acquire stocks of another corporation.'
        WHEN '타법인주식처분' THEN 'It refers to a transaction or decision to sell shares held by another corporation.'
        WHEN '유형자산취득' THEN 'This is a transaction to acquire tangible assets such as land, buildings, and equipment.'
        WHEN '유형자산처분' THEN 'It is a transaction to sell or dispose of owned tangible assets.'
        WHEN '영업정지' THEN 'This is a situation where all or part of the company''s business is suspended.'
        WHEN '생산중단' THEN 'This is a situation where production activities at a factory or business are temporarily or long-term stopped.'
        WHEN '소송등의제기' THEN 'It is a fact that a lawsuit has been filed that could have a significant impact on the company.'
        WHEN '소송등의판결' THEN 'It is the result of a lawsuit ruling that can affect the company''s finances or management.'
        WHEN '횡령배임' THEN 'This is an incident in which executives and employees embezzled company assets or acted in violation of their duties.'
        WHEN '회생절차' THEN 'It is a legal procedure to adjust the debt of a financially difficult company and continue its business.'
        WHEN '파산신청' THEN 'This is the act of requesting the court to initiate bankruptcy proceedings because you cannot repay your debts.'
        WHEN '감사보고서제출' THEN 'This is a public disclosure procedure for submitting an audit report prepared by an external auditor.'
        WHEN '감사의견비적정' THEN 'An audit opinion other than appropriate has been presented, such as a rejection of a limited objection.'
        WHEN '내부회계관리제도' THEN 'It is a company internal control system to ensure the reliability of financial reporting.'
        WHEN '내부회계관리제도검토의견' THEN 'This is the auditor''s review opinion on the operational adequacy of the internal accounting management system.'
        WHEN '주식등의대량보유상황보고서' THEN 'This is a report submitted when you own more than 5% of the stocks of a listed company.'
        WHEN '5퍼센트룰' THEN 'This is a reporting system when more than 5% of stocks of listed companies are held or when there is a change.'
        WHEN '임원주요주주특정증권등소유상황보고서' THEN 'This is a report that informs executives and major shareholders of their holdings and changes in specific securities.'
        WHEN '소유주식변동신고' THEN 'This is a procedure for reporting changes in stocks held by executives or major shareholders.'
        WHEN '단기매매차익반환' THEN 'This is a system that allows insiders to return profits earned from short-term trading to the company.'
        WHEN '공개매수신고서' THEN 'This is a report submitted when attempting to publicly purchase stocks from an unspecified number of people.'
        WHEN '공개매수설명서' THEN 'This is a document that explains the conditions and procedures of the tender offer to investors.'
        WHEN '의결권대리행사권유' THEN 'This is the act of recommending that shareholders exercise their voting rights by proxy.'
        WHEN '참고서류' THEN 'This is additional explanatory material necessary for investor judgment, such as agenda items at general shareholders'' meetings.'
        WHEN '주주총회소집공고' THEN 'This is a notice informing shareholders of the date, time, location, agenda, etc. of the general shareholders’ meeting.'
        WHEN '주주총회소집결의' THEN 'This is a notice informing the board of directors of its decision to hold a general shareholders'' meeting.'
        WHEN '현금현물배당결정' THEN 'This is a decision by the board of directors to distribute dividends in cash or in kind.'
        WHEN '주식배당결정' THEN 'The decision was made to pay dividends in stocks instead of cash.'
        WHEN '자기주식취득결정' THEN 'This is the company''s decision to purchase its own stocks.'
        WHEN '자기주식처분결정' THEN 'This is a decision made by the company to dispose of its own stocks.'
        WHEN '자기주식소각결정' THEN 'This is a decision by the company to reduce the number of shares issued by canceling its own shares.'
        WHEN '전환사채발행결정' THEN 'The decision was made to issue debentures that can be converted into stocks.'
        WHEN '신주인수권부사채발행결정' THEN 'The decision was made to issue bonds with preemptive rights attached.'
        WHEN '교환사채발행결정' THEN 'It is a decision to issue bonds that can be exchanged for stocks held.'
        WHEN '유상증자결정' THEN 'The decision was made to raise funds by issuing new shares.'
        WHEN '무상증자결정' THEN 'The decision was to transfer the surplus to capital and allocate stocks free of charge.'
        WHEN '감자결정' THEN 'It is a decision to reduce the capital or number of issued shares.'
        WHEN '합병결정' THEN 'It is a decision to legally combine two or more companies into one.'
        WHEN '분할결정' THEN 'It is a decision to divide a company''s business or assets into a separate company.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '디레이팅',
        '피어밸류에이션',
        '섹터멀티플',
        '이익수익률',
        'FCF수익률',
        '주주환원수익률',
        'TSR',
        'ROE-PBR 모델',
        '성장가치',
        '자산가치',
        '수익가치',
        '순현금',
        '영업수익',
        '영업비용',
        '판매비와관리비',
        '금융원가',
        '기타수익',
        '기타비용',
        '계속영업손익',
        '중단영업손익',
        '기본주당이익',
        '희석주당이익',
        '가중평균유통주식수',
        '보통주자본',
        '자본금',
        '자본잉여금',
        '이익잉여금',
        '기타자본구성요소',
        '자본조정',
        '기타포괄손익누계액',
        '비유동금융자산',
        '유동금융자산',
        '기타채권',
        '기타채무',
        '선수금',
        '선급금',
        '미수금',
        '미지급금',
        '미수수익',
        '미지급비용',
        '계약자산',
        '계약부채',
        '충당부채전입액',
        '퇴직급여부채',
        '확정급여채무',
        '사외적립자산',
        '재측정요소',
        '영업권',
        '손상차손',
        '손상차손환입',
        '재평가잉여금',
        '감가상각누계액',
        '매출채권손상',
        '대손상각비',
        '현금흐름표',
        '재무상태표',
        '손익계산서',
        '포괄손익계산서',
        '자본변동표',
        '주석',
        '감사의견',
        '계속기업',
        '유동성분류',
        '공정가치',
        '상각후원가',
        '유효이자율',
        '금융보증부채',
        '우발부채',
        '우발자산',
        '영업권손상',
        '재고평가손실',
        '원가율',
        '고정비',
        '변동비',
        '손익분기점',
        '공헌이익',
        '운전자본회전율',
        '경기순환',
        '확장국면',
        '수축국면',
        '경기저점',
        '경기정점',
        '산출갭',
        'GDP갭',
        '필립스곡선',
        '자연실업률',
        'NAIRU',
        '노동생산성',
        'TFP',
        '잠재GDP',
        '총고정자본형성',
        '민간소비',
        '정부소비',
        '민간설비투자',
        '건설투자',
        '지식재산생산물투자',
        '수출',
        '수입',
        '순수출',
        '교역조건',
        '경상거래',
        '자본수지',
        '직접투자',
        '증권투자',
        '기타투자',
        '준비자산',
        '대외채무',
        '대외채권',
        '순대외채권',
        'CDS프리미엄',
        '국가신용등급',
        '재정수지',
        '관리재정수지',
        '통합재정수지',
        '국가채무',
        '조세부담률',
        '소비성향',
        '저축률',
        '가계부채',
        '가계신용',
        '기업신용',
        '신용사이클',
        '금융순환',
        '통화정책파급경로',
        '금리경로',
        '신용경로',
        '환율경로',
        '기대경로',
        '물가안정목표제',
        '기준순환일',
        '생산갭',
        '근원물가',
        '생활물가지수',
        '수입물가지수',
        '수출물가지수',
        '임금상승률',
        '단위노동비용',
        '명목임금',
        '실질임금',
        '경제활동참가율',
        '고용탄력성',
        '장기금리',
        '단기금리',
        '금리차',
        'TED스프레드',
        '역레포',
        '지급결제망',
        'RTGS',
        '순액결제',
        '외환건전성부담금',
        '거시건전성정책',
        '정기공시',
        '수시공시',
        '공정공시',
        '자율공시',
        '조회공시',
        '해명공시',
        '풍문또는보도',
        '공급계약',
        '단일판매공급계약',
        '단기차입금증가결정',
        '타법인주식취득',
        '타법인주식처분',
        '유형자산취득',
        '유형자산처분',
        '영업정지',
        '생산중단',
        '소송등의제기',
        '소송등의판결',
        '횡령배임',
        '회생절차',
        '파산신청',
        '감사보고서제출',
        '감사의견비적정',
        '내부회계관리제도',
        '내부회계관리제도검토의견',
        '주식등의대량보유상황보고서',
        '5퍼센트룰',
        '임원주요주주특정증권등소유상황보고서',
        '소유주식변동신고',
        '단기매매차익반환',
        '공개매수신고서',
        '공개매수설명서',
        '의결권대리행사권유',
        '참고서류',
        '주주총회소집공고',
        '주주총회소집결의',
        '현금현물배당결정',
        '주식배당결정',
        '자기주식취득결정',
        '자기주식처분결정',
        '자기주식소각결정',
        '전환사채발행결정',
        '신주인수권부사채발행결정',
        '교환사채발행결정',
        '유상증자결정',
        '무상증자결정',
        '감자결정',
        '합병결정',
        '분할결정'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '영업양수결정' THEN 'It is a decision to take over all or part of the business of another company.'
        WHEN '영업양도결정' THEN 'It is a decision to transfer all or part of a company''s operations to another company.'
        WHEN '최대주주변경' THEN 'This means that the company''s largest shareholder has changed.'
        WHEN '대표이사변경' THEN 'This is a fact that has changed due to a new CEO being appointed or resigning.'
        WHEN '임원변동' THEN 'Changes include the appointment, resignation, or dismissal of registered executives or key executives.'
        WHEN '상장적격성실질심사' THEN 'This is a process where the exchange actually reviews whether there are any serious problems with maintaining listing.'
        WHEN '거래소조회공시' THEN 'This is a disclosure in which a listed company responds to matters requested by the exchange.'
        WHEN '투자주의환기종목' THEN 'It is a stock that requires investor caution in the KOSDAQ market due to high financial and internal control risks.'
        WHEN '투자경고종목' THEN 'It has been designated as a stock that requires investor warning due to a surge in stock prices.'
        WHEN '투자위험종목' THEN 'This stock is subject to strong caution by exchanges due to its high risk of speculative trading.'
        WHEN '단기과열종목' THEN 'This stock is subject to mitigation measures due to overheated trading in a short period of time.'
        WHEN '매매거래재개' THEN 'Trading of stocks that were suspended has started again.'
        WHEN '관리종목지정사유' THEN 'This is due to the financial disclosure transaction requirements for designation as a managed item.'
        WHEN '상장폐지사유' THEN 'This is a reason that prevents it from meeting the requirements for maintaining listing.'
        WHEN '기업지배구조보고서' THEN 'This is a report explaining the governance policy and operational status of a listed company.'
        WHEN '지속가능경영보고서' THEN 'This is a report that explains sustainability information such as environment, social governance, etc.'
        WHEN '주가' THEN 'It is the price of one share of stock traded on the market.'
        WHEN '시가' THEN 'It is the transaction price first formed at the regular market or at the standard time.'
        WHEN '고가' THEN 'It is the highest transaction price during a given period.'
        WHEN '저가' THEN 'It is the lowest transaction price during a given period.'
        WHEN '종가' THEN 'This is the last transaction price established at the end of regular trading.'
        WHEN '전일종가' THEN 'The closing price of the previous trading day serves as the standard for comparing prices on the same day.'
        WHEN '기준가' THEN 'This is the standard price used when calculating the price limit or evaluation standard.'
        WHEN '시초가' THEN 'This is the price first entered into at the start of the market.'
        WHEN '예상체결가' THEN 'This is the expected execution price based on the current asking price during a single price transaction.'
        WHEN '예상체결량' THEN 'This is the expected execution quantity based on the current asking price during a single price transaction.'
        WHEN '호가' THEN 'This is the purchase or sale price and quantity suggested by the investor.'
        WHEN '매수호가' THEN 'This is the price and quantity that the investor proposes to purchase.'
        WHEN '매도호가' THEN 'This is the price and quantity that the investor proposes to sell.'
        WHEN '최우선매수호가' THEN 'This is the highest price among buy orders.'
        WHEN '최우선매도호가' THEN 'This is the lowest price among sell orders.'
        WHEN '매수잔량' THEN 'This is the quantity of buy orders remaining that have not yet been filled.'
        WHEN '매도잔량' THEN 'This is the quantity of sell orders remaining that have not yet been filled.'
        WHEN '시장충격비용' THEN 'This is a transaction cost that arises when large orders move the price unfavorably.'
        WHEN '시장유동성' THEN 'This is the extent to which you can buy and sell quickly in the market at the desired price.'
        WHEN '호가공백' THEN 'There is a gap between adjacent bid and ask prices.'
        WHEN '틱' THEN 'It refers to the smallest unit by which price can move or individual price changes.'
        WHEN '틱사이즈' THEN 'This is the minimum price unit by which the transaction price can change.'
        WHEN '가격발견' THEN 'It is the process of finding a fair market price when buy and sell orders meet.'
        WHEN '장중' THEN 'This refers to the time period during which regular trading takes place.'
        WHEN '프리마켓' THEN 'It is an after-hours market where trading is possible before the start of regular trading.'
        WHEN '애프터마켓' THEN 'It is an after-hours market where trading is possible after the regular trading session closes.'
        WHEN '공매도잔고' THEN 'This is the remaining amount of short selling positions that have not yet been repaid.'
        WHEN '공매도거래대금' THEN 'It is the total amount of transactions concluded through short selling orders.'
        WHEN '대차잔고' THEN 'This is the remaining amount of stocks that have been borrowed but not yet repaid.'
        WHEN '대차상환' THEN 'It is a transaction or procedure to return borrowed stocks.'
        WHEN '대차체결' THEN 'A lending transaction has been established between the stock lender and the borrower.'
        WHEN '리콜' THEN 'This is an act of requesting the return of stocks lent by the lender.'
        WHEN '차입공매도' THEN 'It is a short selling method that first borrows stocks and then sells them.'
        WHEN '무차입공매도' THEN 'This is a prohibited short selling method in which stocks are sold without borrowing them.'
        WHEN '결제불이행' THEN 'Securities or money cannot be paid on the designated payment date.'
        WHEN 'T+2' THEN 'It refers to the stock market settlement cycle in which settlement occurs two business days after the transaction date.'
        WHEN '예탁결제' THEN 'It is a procedure in which the storage of securities and settlement of trading prices are processed through a central agency.'
        WHEN '주식대여' THEN 'This is a transaction in which the stocks held are loaned to other investors for a certain period of time.'
        WHEN '담보비율' THEN 'In a borrowing or credit transaction, it is the ratio of the value of collateral to the debt.'
        WHEN '담보부족' THEN 'The collateral value is lower than the required level, so additional collateral is needed.'
        WHEN '증거금률' THEN 'It is the ratio of the margin that must be deposited for trading compared to the contract amount.'
        WHEN '위탁증거금률' THEN 'This is the margin rate that the consignor must pay in advance to place an order.'
        WHEN '유지증거금률' THEN 'This is the minimum margin ratio that must be secured to maintain a position.'
        WHEN '추가증거금' THEN 'This is an additional amount that must be paid when collateral or margin is insufficient.'
        WHEN '청산소' THEN 'It is an institution that guarantees and settles the payment of transactions such as derivatives.'
        WHEN 'CCP' THEN 'It is a central clearing agency that intervenes between transaction parties to reduce settlement risk.'
        WHEN '일중가격제한' THEN 'It is a price limiting device applied to prevent sudden intraday price changes.'
        WHEN '동적VI' THEN 'It is a volatility mitigation device that is activated when the price changes suddenly on a short-term basis, such as the previous transaction price.'
        WHEN '정적VI' THEN 'It is a volatility mitigation device that is activated when the price changes rapidly from a static standard such as the previous day''s closing price.'
        WHEN '변동성완화장치' THEN 'This is a system that reduces volatility by switching to single price trading when the price changes suddenly.'
        WHEN '랜덤엔드' THEN 'This is a method of suppressing imaginary orders by randomly adjusting the end point of single price trading.'
        WHEN '종가단일가' THEN 'It is a trading method in which orders are collected for a certain period of time before the market closes and executed at one price.'
        WHEN '신규상장' THEN 'This is the first time a new security can be traded on the exchange market.'
        WHEN '재상장' THEN 'It is the process or state of being re-listed after delisting or split.'
        WHEN '이전상장' THEN 'It is a matter of moving a listed market from one market to another.'
        WHEN '변경상장' THEN 'This is a process in which listing information, such as the number of shares at par, is changed and reflected.'
        WHEN '투자유의종목' THEN 'This is a stock that exchanges require caution to protect investors.'
        WHEN '단기과열완화장치' THEN 'It is a trading restriction device applied to alleviate short-term stock price surges and overheated trading.'
        WHEN '시장감시' THEN 'This is an exchange function that detects and prevents unfair transactions and abnormal transactions.'
        WHEN '이상거래' THEN 'A transaction in which price or volume moves abnormally, unlike normal transaction patterns.'
        WHEN '매매심리' THEN 'This is a process that examines investor orders and transactions to determine whether there are unfair transactions.'
        WHEN '결제월' THEN 'This refers to the month in which the derivatives contract is finally settled.'
        WHEN '최근월물' THEN 'This is the derivative contract with the closest maturity.'
        WHEN '차근월물' THEN 'It is a derivative contract with the closest maturity after the front-month contract.'
        WHEN '해외ETF' THEN 'This is an ETF that tracks overseas assets or foreign market indices.'
        WHEN '국내ETF' THEN 'This is an ETF that tracks domestic assets or domestic market indices.'
        WHEN '액티브ETF' THEN 'This is an ETF that pursues excess returns through the manager''s strategy rather than simply tracking the index.'
        WHEN '패시브ETF' THEN 'This is an ETF that is managed to follow the returns of the underlying index.'
        WHEN '커버드콜ETF' THEN 'It is an ETF that seeks distribution resources by combining holding underlying assets and selling call options.'
        WHEN '채권ETF' THEN 'It is an ETF that uses bonds or bond indices as underlying assets.'
        WHEN '원자재ETF' THEN 'This is an ETF that tracks the prices of raw materials such as gold and crude oil or related indices.'
        WHEN '통화ETF' THEN 'It is an ETF that uses exchange rates or currency-related indices as its underlying assets.'
        WHEN '테마ETF' THEN 'This is an ETF designed to follow specific industry trends or investment themes.'
        WHEN '섹터ETF' THEN 'This is an ETF that tracks a specific industry or industry index.'
        WHEN '스마트베타ETF' THEN 'It is an ETF that constructs an index by utilizing factors other than market capitalization, such as value and dividend volatility.'
        WHEN '동일가중ETF' THEN 'It is an ETF designed to contain constituent stocks in equal proportions.'
        WHEN '합성복제' THEN 'It is an operation method that replicates the performance of the underlying index by utilizing derivative contracts such as swaps.'
        WHEN '실물복제' THEN 'It is a management method that directly owns the constituent stocks of the basic index and follows the index performance.'
        WHEN '환헤지형ETF' THEN 'This is an ETF that applies a hedging strategy to reduce the impact of exchange rate fluctuations.'
        WHEN '환노출형ETF' THEN 'This is an ETF that reflects the impact of exchange rate fluctuations without separately removing them.'
        WHEN '레버리지ETN' THEN 'It is an ETN that pursues multiple performance of the underlying index return.'
        WHEN '인버스ETN' THEN 'It is an ETN that pursues performance in the opposite direction to the underlying index.'
        WHEN '조기상환형ELS' THEN 'This is an ELS that is repaid before maturity if conditions are met on a designated valuation date.'
        WHEN '원금지급형ELS' THEN 'It is an ELS with a principal payment structure according to maturity conditions.'
        WHEN '원금비보장형ELS' THEN 'This is an ELS that may result in principal loss depending on the conditions of a decline in the underlying asset.'
        WHEN '낙인' THEN 'This means that the price of the underlying asset reaches a designated loss range.'
        WHEN '노낙인' THEN 'This refers to a structure that does not impose a separate stigma section as a condition for loss occurrence.'
        WHEN '베리어' THEN 'This is the standard price level that becomes a condition for redemption or loss in derivatives-linked products.'
        WHEN '기초자산바스켓' THEN 'It is a bundle of several underlying assets that determine profit and loss conditions in ELS, etc.'
        WHEN '자동조기상환' THEN 'If the conditions are met on the evaluation date, early repayment is made without investor request.'
        WHEN '월지급식ELS' THEN 'It is an ELS that pays coupons or distributions on a monthly basis according to profit conditions.'
        WHEN '리츠배당' THEN 'This is the amount that REITs distribute rental income and sales profits to investors.'
        WHEN '공모리츠' THEN 'These are REITs that are listed or managed through public recruitment to a large number of investors.'
        WHEN '사모리츠' THEN 'It is a REIT established and managed through a private placement targeting minority investors.'
        WHEN '인프라펀드' THEN 'It is a fund that invests in infrastructure assets such as roads, ports, and energy.'
        WHEN '부동산펀드' THEN 'It is a fund that invests in real estate or real estate-related rights.'
        WHEN '특별자산펀드' THEN 'It is a fund that invests in real assets and rights, excluding real estate.'
        WHEN '재간접펀드' THEN 'It is a fund that forms a portfolio by mainly investing in other funds.'
        WHEN '모자형펀드' THEN 'It is a fund structured in which several sub-funds invest in one parent fund.'
        WHEN '종류형펀드' THEN 'It is a fund divided into several classes with different fee and compensation structures.'
        WHEN '판매클래스' THEN 'It is a class divided according to fund sales channel or fee method.'
        WHEN '펀드환매기준가' THEN 'This is the standard price applied when calculating the fund redemption amount.'
        WHEN '펀드결산' THEN 'This is a procedure to determine the fund’s profits and losses and distribution during the accounting period.'
        WHEN '기준가격변동' THEN 'This is a phenomenon in which the fund''s base price changes to reflect management performance and costs.'
        WHEN '세전수익률' THEN 'It is the return on investment before taxes are deducted.'
        WHEN '세후수익률' THEN 'This is the actual rate of return left to the investor after deducting taxes.'
        WHEN '과세표준기준가격' THEN 'This is the base price used to calculate fund taxable profits.'
        WHEN '보수율' THEN 'It is the ratio of compensation charged for a certain period of time in a fund or financial product.'
        WHEN '매매수수료' THEN 'This is a commission paid to securities companies when buying or selling securities.'
        WHEN '신탁보수' THEN 'This is compensation paid for the storage and management of trust property.'
        WHEN 'TER' THEN 'It is a value expressed as a ratio of the total cost of fund operation and management to net assets.'
        WHEN '합성총보수' THEN 'This is the total cost borne by the investor by adding up the basic compensation and other costs.'
        WHEN '퇴직연금계좌' THEN 'This is an account opened to accumulate and manage retirement benefits.'
        WHEN '개인연금' THEN 'This is a pension product that individuals subscribe to and operate to secure retirement funds.'
        WHEN '연금수령' THEN 'This is a process in which accumulated pension assets are divided in a set manner.'
        WHEN '연금개시' THEN 'This is the point at which pension accumulation period ends and receipt of pension begins.'
        WHEN '세액공제한도' THEN 'This is the maximum amount that can receive a tax deduction among pension savings or IRP payments.'
        WHEN '중도인출' THEN 'This is the act of seeking out and using up part of a pension or financial product before its maturity.'
        WHEN 'SIGNAL SCORE' THEN 'It is a score that expresses the relative attractiveness of a stock by adding up several input signals.'
        WHEN 'PRICE MOMENTUM' THEN 'It is an indicator that shows the extent to which price trends continue in the same direction over a certain period of time.'
        WHEN 'EARNINGS MOMENTUM' THEN 'It is an indicator that shows the speed at which performance forecasts or profit estimates are improving.'
        WHEN 'QUALITY SCORE' THEN 'This score represents the quality of the company by combining profitability, stability, and financial soundness.'
        WHEN 'VALUE SCORE' THEN 'This score indicates the degree of undervaluation by combining valuation indicators.'
        WHEN 'GROWTH SCORE' THEN 'This score is a composite of growth potential, including sales, profits, and assets.'
        WHEN 'RISK SCORE' THEN 'This is a score that represents the risk level by combining volatility, financial risk, and event risk.'
        WHEN 'LIQUIDITY SCORE' THEN 'It is a score that indicates liquidity by combining transaction amount, bid, bid, spread, and turnover rate.'
        WHEN 'SENTIMENT SCORE' THEN 'It is a score that quantifies qualitative signals such as news announcements and market reactions.'
        WHEN 'DISCLOSURE EVENT' THEN 'This is a major corporate event unit extracted from public announcements.'
        WHEN 'EVENT WINDOW' THEN 'This is an analysis period set to measure the impact before and after an event.'
        WHEN 'ABNORMAL RETURN' THEN 'It is excess return that cannot be explained by the market or standard model.'
        WHEN 'BASELINE RETURN' THEN 'This is the standard rate of return for comparing event effects.'
        WHEN 'FACTOR EXPOSURE' THEN 'This is the degree to which a portfolio or stock is exposed to changes in a specific factor.'
        WHEN 'FACTOR NEUTRAL' THEN 'The influence of specific factors has been neutralized to prevent them from being excessively reflected in performance.'
        WHEN 'UNIVERSE' THEN 'It is a set of stocks included as candidates for analysis or investment.'
        WHEN 'WATCHLIST' THEN 'This is a separate list of stocks that require follow-up.'
        WHEN 'SCORING MODEL' THEN 'It is a system that creates a score by combining multiple indicators into rules or models.'
        WHEN 'FEATURE IMPORTANCE' THEN 'This is the relative importance that each input variable contributes to the result in model prediction.'
        WHEN 'DATA COVERAGE' THEN 'This is the percentage of the analysis subjects for which the necessary data has been secured.'
        WHEN 'DATA FRESHNESS' THEN 'This is the degree to which the data reflects the current status at the reference point.'
        WHEN 'OUTLIER FILTER' THEN 'This is a rule to exclude or adjust extreme values ​​so that they do not distort the analysis results.'
        WHEN 'WINSORIZATION' THEN 'This is an outlier processing method that limits extreme values ​​to certain quantile boundary values.'
        WHEN 'Z-SCORE' THEN 'It is a standardized score that indicates how many times the standard deviation a value is away from the mean.'
        WHEN 'PERCENTILE RANK' THEN 'This is a rank that indicates which percentile position the observed value is in the entire distribution.'
        WHEN 'ROLLING WINDOW' THEN 'This is a method of repeatedly calculating indicators while moving a period of a certain length.'
        WHEN 'REBALANCE SIGNAL' THEN 'This is a signal that the portfolio proportion or stock composition needs to be adjusted.'
        WHEN 'TURNOVER LIMIT' THEN 'This is a limiting standard set to prevent the trading turnover rate from becoming excessive.'
        WHEN 'POSITION CAP' THEN 'This is the maximum holding limit for a specific stock or group.'
        WHEN 'RISK BUDGET' THEN 'This is the allowable risk limit assigned to a strategy or portfolio.'
        WHEN '잔차수익률' THEN 'This is the portion of return that remains unexplained by the market or factors.'
        WHEN '젠센알파' THEN 'It is a measure of excess performance exceeding the expected rate of return explained by CAPM.'
        WHEN '트래킹알파' THEN 'It is an indicator that distinguishes excess performance that occurred during the benchmark tracking process.'
        WHEN '액티브리스크' THEN 'It refers to the volatility of excess return compared to the benchmark.'
        WHEN '위험기여도' THEN 'It is the contribution of a specific asset or factor to the overall risk of the portfolio.'
        WHEN '트레이너비율' THEN 'It is an indicator that evaluates performance relative to market risk by dividing excess return by beta.'
        WHEN '한계위험기여도' THEN 'It indicates how much the portfolio risk changes when the proportion of a specific asset is slightly increased.'
        WHEN '칼마비율' THEN 'The annualized rate of return is divided by the maximum decline to evaluate performance relative to the decline.'
        WHEN '포트폴리오분산' THEN 'It is a risk indicator calculated by calculating the variance of the portfolio return rate.'
        WHEN '하방변동성' THEN 'It is a risk indicator that reflects only the return range that is lower than the target rate of return.'
        WHEN '표준편차' THEN 'It is a statistic that indicates how scattered the observed values ​​are from the mean.'
        WHEN '분산' THEN 'It is a volatility indicator that averages the squared deviation from the average of observed values.'
        WHEN '공분산행렬' THEN 'It is a value that organizes the covariance between returns on various assets in matrix form.'
        WHEN '상관행렬' THEN 'It is a value that organizes the correlation coefficients between multiple assets in matrix form.'
        WHEN '위험조정수익률' THEN 'This is a comparison of returns by adjusting them with volatility or risk indicators.'
        WHEN '최대손실기간' THEN 'This is the period of loss after the peak before recovering the previous peak.'
        WHEN '평균보유기간' THEN 'This is the average holding period between buying and selling a position.'
        WHEN '기간수익률' THEN 'It is a rate of return that represents investment performance between a specific start date and end date.'
        WHEN '로그수익률' THEN 'It is a rate of return calculated as the natural logarithm of the price change rate.'
        WHEN '단순수익률' THEN 'It is a rate of return calculated as a simple ratio of price change and cash flow compared to the purchase price.'
        WHEN '성과기여도' THEN 'This is the portion of the total return contributed by a specific asset class or factor.'
        WHEN '섹터기여도' THEN 'This is the extent to which industry or sector selection contributes to portfolio performance.'
        WHEN '최대상승폭' THEN 'It refers to the maximum increase from the low point to the high point.'
        WHEN '종목기여도' THEN 'It is the contribution made by individual stocks to the overall performance.'
        WHEN '자산군기여도' THEN 'This is the contribution to performance by asset class, such as stocks, bonds, and cash.'
        WHEN '기대값' THEN 'It is an average expected value that reflects possible outcomes and probabilities.'
        WHEN '손절' THEN 'This is the act of terminating a position based on a set standard to prevent losses from expanding.'
        WHEN '익절' THEN 'This is the act of closing positions and confirming profits when the target profit is reached.'
        WHEN '손절가' THEN 'This is the price set for selling or liquidation to limit losses.'
        WHEN '목표수익률' THEN 'This is the level of return set to be achieved in a strategy or investment.'
        WHEN '목표손실률' THEN 'This is a level set as an allowable loss rate for risk management.'
        WHEN '포지션사이징' THEN 'This is the process of determining the investment proportion or quantity by reflecting the risk limit and degree of certainty.'
        WHEN '켈리기준' THEN 'It is a standard for calculating the theoretical optimal betting proportion using the win rate and profit/loss ratio.'
        WHEN 'VaR' THEN 'It is a risk indicator that estimates the maximum amount of loss that can be expected at a certain confidence level.'
        WHEN 'CVaR' THEN 'It is a tail risk indicator that looks at the average loss in the loss section that exceeds VaR.'
        WHEN '팩터기여도' THEN 'This is the extent to which factors such as value growth momentum contribute to overall performance or risk.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '영업양수결정',
        '영업양도결정',
        '최대주주변경',
        '대표이사변경',
        '임원변동',
        '상장적격성실질심사',
        '거래소조회공시',
        '투자주의환기종목',
        '투자경고종목',
        '투자위험종목',
        '단기과열종목',
        '매매거래재개',
        '관리종목지정사유',
        '상장폐지사유',
        '기업지배구조보고서',
        '지속가능경영보고서',
        '주가',
        '시가',
        '고가',
        '저가',
        '종가',
        '전일종가',
        '기준가',
        '시초가',
        '예상체결가',
        '예상체결량',
        '호가',
        '매수호가',
        '매도호가',
        '최우선매수호가',
        '최우선매도호가',
        '매수잔량',
        '매도잔량',
        '시장충격비용',
        '시장유동성',
        '호가공백',
        '틱',
        '틱사이즈',
        '가격발견',
        '장중',
        '프리마켓',
        '애프터마켓',
        '공매도잔고',
        '공매도거래대금',
        '대차잔고',
        '대차상환',
        '대차체결',
        '리콜',
        '차입공매도',
        '무차입공매도',
        '결제불이행',
        'T+2',
        '예탁결제',
        '주식대여',
        '담보비율',
        '담보부족',
        '증거금률',
        '위탁증거금률',
        '유지증거금률',
        '추가증거금',
        '청산소',
        'CCP',
        '일중가격제한',
        '동적VI',
        '정적VI',
        '변동성완화장치',
        '랜덤엔드',
        '종가단일가',
        '신규상장',
        '재상장',
        '이전상장',
        '변경상장',
        '투자유의종목',
        '단기과열완화장치',
        '시장감시',
        '이상거래',
        '매매심리',
        '결제월',
        '최근월물',
        '차근월물',
        '해외ETF',
        '국내ETF',
        '액티브ETF',
        '패시브ETF',
        '커버드콜ETF',
        '채권ETF',
        '원자재ETF',
        '통화ETF',
        '테마ETF',
        '섹터ETF',
        '스마트베타ETF',
        '동일가중ETF',
        '합성복제',
        '실물복제',
        '환헤지형ETF',
        '환노출형ETF',
        '레버리지ETN',
        '인버스ETN',
        '조기상환형ELS',
        '원금지급형ELS',
        '원금비보장형ELS',
        '낙인',
        '노낙인',
        '베리어',
        '기초자산바스켓',
        '자동조기상환',
        '월지급식ELS',
        '리츠배당',
        '공모리츠',
        '사모리츠',
        '인프라펀드',
        '부동산펀드',
        '특별자산펀드',
        '재간접펀드',
        '모자형펀드',
        '종류형펀드',
        '판매클래스',
        '펀드환매기준가',
        '펀드결산',
        '기준가격변동',
        '세전수익률',
        '세후수익률',
        '과세표준기준가격',
        '보수율',
        '매매수수료',
        '신탁보수',
        'TER',
        '합성총보수',
        '퇴직연금계좌',
        '개인연금',
        '연금수령',
        '연금개시',
        '세액공제한도',
        '중도인출',
        'SIGNAL SCORE',
        'PRICE MOMENTUM',
        'EARNINGS MOMENTUM',
        'QUALITY SCORE',
        'VALUE SCORE',
        'GROWTH SCORE',
        'RISK SCORE',
        'LIQUIDITY SCORE',
        'SENTIMENT SCORE',
        'DISCLOSURE EVENT',
        'EVENT WINDOW',
        'ABNORMAL RETURN',
        'BASELINE RETURN',
        'FACTOR EXPOSURE',
        'FACTOR NEUTRAL',
        'UNIVERSE',
        'WATCHLIST',
        'SCORING MODEL',
        'FEATURE IMPORTANCE',
        'DATA COVERAGE',
        'DATA FRESHNESS',
        'OUTLIER FILTER',
        'WINSORIZATION',
        'Z-SCORE',
        'PERCENTILE RANK',
        'ROLLING WINDOW',
        'REBALANCE SIGNAL',
        'TURNOVER LIMIT',
        'POSITION CAP',
        'RISK BUDGET',
        '잔차수익률',
        '젠센알파',
        '트래킹알파',
        '액티브리스크',
        '위험기여도',
        '트레이너비율',
        '한계위험기여도',
        '칼마비율',
        '포트폴리오분산',
        '하방변동성',
        '표준편차',
        '분산',
        '공분산행렬',
        '상관행렬',
        '위험조정수익률',
        '최대손실기간',
        '평균보유기간',
        '기간수익률',
        '로그수익률',
        '단순수익률',
        '성과기여도',
        '섹터기여도',
        '최대상승폭',
        '종목기여도',
        '자산군기여도',
        '기대값',
        '손절',
        '익절',
        '손절가',
        '목표수익률',
        '목표손실률',
        '포지션사이징',
        '켈리기준',
        'VaR',
        'CVaR',
        '팩터기여도'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '오차항' THEN 'It is the difference between the actual value and the predicted value that the model cannot explain.'
        WHEN '잔차변동성' THEN 'It indicates the degree to which model residuals fluctuate over time.'
        WHEN '베타노출' THEN 'This is the degree to which a portfolio is exposed to changes in the market or specific factors.'
        WHEN '전진분석' THEN 'This is a method of evaluating by sequentially increasing the actual verification section after the past learning section.'
        WHEN '알파감쇠' THEN 'This is a phenomenon in which the initially observed excess and signal weaken over time.'
        WHEN '신호반감기' THEN 'This is the period of time it takes for the effectiveness of an investment signal to be reduced by half.'
        WHEN '체결확률' THEN 'This is an estimate of the likelihood that an order will actually be executed under specified conditions.'
        WHEN '세금효과' THEN 'This refers to the impact of taxes on investment returns and cash flow.'
        WHEN '체결가정' THEN 'This is the standard that determines at what price and quantity the order will be considered to have been executed in the backtest.'
        WHEN '매매빈도' THEN 'It refers to the number or frequency of trading in a strategy or account over a certain period of time.'
        WHEN '보유기간수익률' THEN 'It is the total rate of return that occurred during the period the asset was held.'
        WHEN '초과성과' THEN 'This is an investment performance that is higher than the benchmark or target rate of return.'
        WHEN '해외주식' THEN 'This refers to stocks listed on foreign exchanges, not domestic ones.'
        WHEN '미국주식' THEN 'This refers to stocks traded on U.S. exchanges or over-the-counter markets.'
        WHEN '미국정규장' THEN 'This refers to the regular trading hours of the U.S. stock market.'
        WHEN '미국프리마켓' THEN 'This refers to the time period during which trading is possible before the start of the U.S. regular market.'
        WHEN '미국애프터마켓' THEN 'This refers to the time period during which trading is possible after the US regular trading session closes.'
        WHEN '야간거래' THEN 'It is a method of trading overseas stocks or derivatives during domestic night time.'
        WHEN '주간거래' THEN 'It is a service that trades foreign stocks through a brokerage system during domestic daytime hours.'
        WHEN '환전우대' THEN 'This is a condition of receiving a discount on the exchange fee compared to the standard exchange rate when buying or selling foreign currency.'
        WHEN '통합증거금' THEN 'It is a system that combines the appraised value of several currencies or assets and uses it as an order margin.'
        WHEN '외화증거금' THEN 'It is a margin held or calculated in foreign currency for trading foreign currency products.'
        WHEN '원화증거금' THEN 'It is an order margin calculated or deposited based on Korean Won.'
        WHEN '예수금' THEN 'It is a cash balance that is stored in an account and can be used for trading or withdrawal.'
        WHEN '주문가능금액' THEN 'This is the amount that can be used for new orders under the current account status.'
        WHEN '출금가능금액' THEN 'This is the actual amount that can be withdrawn, reflecting payment and margin restrictions.'
        WHEN '매수가능금액' THEN 'This is the amount that can be used for a buy order, reflecting fees and margins.'
        WHEN '미수거래' THEN 'It is a method of trading under the condition that the insufficient purchase price is paid within a certain period of time before settlement.'
        WHEN '미수동결' THEN 'Due to non-payment of receivables, receivable transactions are restricted for a certain period of time.'
        WHEN '반대매매예정' THEN 'A forced sale is expected due to non-met collateral or outstanding requirements.'
        WHEN '담보대출' THEN 'This is a loan in which funds are borrowed from a financial company using assets held as collateral.'
        WHEN '주식담보대출' THEN 'This is a loan product in which you borrow money by providing your stocks as collateral.'
        WHEN '신용공여' THEN 'This is a credit provision in which a securities company lends purchase funds or stocks to customers.'
        WHEN '신용매수' THEN 'This is a transaction where you borrow funds from a securities company to purchase stocks.'
        WHEN '신용상환' THEN 'This is the process of repaying funds or stocks borrowed through credit transactions.'
        WHEN '융자잔고' THEN 'This is the amount or quantity purchased with a credit loan but not yet repaid.'
        WHEN '대주잔고' THEN 'This is the remaining stock that has been borrowed through credit lending but has not yet been repaid.'
        WHEN '연체이자' THEN 'This is additional interest charged when repayment or payment is late.'
        WHEN '권리정보' THEN 'These are rights that accrue to the stocks held, such as dividends, paid-in capital increase, and stock splits.'
        WHEN '배당입금' THEN 'This is the process whereby dividends are received as cash in the investor’s account.'
        WHEN '배당락일' THEN 'This is the base date when the stock price reflects the dividend amount as the right to receive dividends disappears.'
        WHEN '지급일' THEN 'This is the date on which dividends, interest, principal, etc. are actually paid.'
        WHEN '기준통화' THEN 'This is the currency used as a standard when calculating rates of return or valuation.'
        WHEN '결제통화' THEN 'This is the currency in which the transaction amount is actually paid.'
        WHEN '원화환산금액' THEN 'This is the amount converted from foreign currency assets or cash flows to Korean won.'
        WHEN '평가금액' THEN 'This is the amount that evaluates the assets held at the current price or standard price.'
        WHEN '평가손익' THEN 'It is a profit or loss calculated as the difference between the assessed value of the assets held and the purchase price.'
        WHEN '실현손익' THEN 'It is a profit or loss confirmed by sale or liquidation.'
        WHEN '미실현손익' THEN 'It is a gain or loss on valuation that has not yet been sold and has not yet been confirmed.'
        WHEN '평균단가' THEN 'It is the average acquisition price of assets purchased multiple times.'
        WHEN '매입단가' THEN 'It is the standard price at which an asset is purchased and serves as the basis for calculating the average unit price.'
        WHEN '수익률알림' THEN 'This is a function that notifies you when the target return or loss rate is reached.'
        WHEN '조건검색' THEN 'It is a search function that finds stocks by combining price indicators, financial conditions, etc.'
        WHEN '자동주문' THEN 'This function automatically submits orders when pre-determined conditions are met.'
        WHEN '예약주문' THEN 'This is an order that is received in advance before the regular ordering time and submitted at a designated time.'
        WHEN '스탑주문' THEN 'This is an order that is converted to a market price or limit price order when the specified price condition is reached.'
        WHEN '트레일링스탑' THEN 'If the price moves favorably, the stop loss standard increases or decreases accordingly.'
        WHEN 'OCO주문' THEN 'This is an ordering method in which when one of two orders is executed, the remaining order is automatically canceled.'
        WHEN 'Bracket주문' THEN 'It is an ordering method that manages entry orders and take-profit stop-loss orders together.'
        WHEN 'LOC' THEN 'It is a limit price order that aims to be executed when the closing price of U.S. stocks meets the specified price conditions.'
        WHEN 'MOC' THEN 'It is a market order that aims to be executed at the closing price of the US stock market.'
        WHEN 'VWAP주문' THEN 'This is an order that is executed in installments so that it is executed close to the volume-weighted average price.'
        WHEN 'TWAP주문' THEN 'This is an order that is divided and executed by time to be executed close to the time-weighted average price.'
        WHEN '빙산주문' THEN 'This is an ordering method that exposes only a portion of the total order quantity to the market and hides the rest.'
        WHEN '분할주문' THEN 'This is a method of reducing market shock by submitting large orders multiple times.'
        WHEN '주문유효기간' THEN 'This is a condition for the period during which an order remains valid without being cancelled.'
        WHEN '당일주문' THEN 'This order condition is valid only until the market closes on the day the order is placed.'
        WHEN 'GTC' THEN 'Refers to order conditions that remain in effect until cancellation.'
        WHEN 'GTD' THEN 'This refers to order conditions that remain valid until a specified date.'
        WHEN '부분체결' THEN 'Only a portion of the ordered quantity has been completed.'
        WHEN '미체결주문' THEN 'This is an order that has not yet been completed.'
        WHEN '체결내역' THEN 'This is a record of the price, quantity, and time at which the order was actually traded.'
        WHEN '정정주문' THEN 'This is an order that changes the conditions such as price or quantity of the received order.'
        WHEN '취소주문' THEN 'This is an order to cancel an order that has not yet been executed.'
        WHEN '잔량취소' THEN 'This is the process of canceling the remaining order quantity after a partial transaction.'
        WHEN '주문거부' THEN 'Orders have not been accepted due to price, quantity, and margin restrictions.'
        WHEN '주문접수' THEN 'The trading system has received the order and registered it as waiting for processing.'
        WHEN '주문번호' THEN 'This is a unique number assigned by the trading system to identify the order.'
        WHEN '원장잔고' THEN 'This is the official account balance managed based on the financial company ledger.'
        WHEN '실시간잔고' THEN 'It is an account balance that reflects transactions and valuation changes in close real time.'
        WHEN '체결기준잔고' THEN 'It is the quantity and amount held based on concluded transactions.'
        WHEN '결제기준잔고' THEN 'This is the quantity held and cash balance calculated by reflecting the actual payment date.'
        WHEN '평가손익률' THEN 'It is a ratio comparing the valuation profit and loss with the purchase price.'
        WHEN '발행어음' THEN 'It is a short-term financial product issued by securities companies on their own credit and sold to investors.'
        WHEN '외화RP' THEN 'It is a repurchase agreement product traded in foreign currency.'
        WHEN '달러RP' THEN 'It is a repurchase agreement bond product purchased in US dollars.'
        WHEN 'MMDA' THEN 'This refers to a high-interest deposit account with a bank that allows frequent deposits and withdrawals.'
        WHEN 'ISA중개형' THEN 'This is a type of ISA in which investors directly select and manage domestic stock ETF funds.'
        WHEN 'ISA신탁형' THEN 'This is a type of ISA that instructs a financial company to manage assets in a trust manner.'
        WHEN 'ISA일임형' THEN 'This is a type of ISA in which a financial company manages its portfolio according to investor preferences.'
        WHEN '연금저축펀드' THEN 'It is a fund-type product operated through a pension savings account that can receive tax deduction benefits.'
        WHEN '연금저축보험' THEN 'It is a pension savings product provided by an insurance company and has an insurance structure.'
        WHEN '퇴직연금ETF' THEN 'This is an ETF that can be traded or added to a retirement pension account.'
        WHEN 'TDF빈티지' THEN 'TDF is a target year that is classified based on the target retirement time.'
        WHEN 'TIF' THEN 'This is a type of fund that aims to manage cash flow during the withdrawal period after retirement.'
        WHEN '공모주청약' THEN 'This is a process for general investors to apply for allocation of newly listed stocks.'
        WHEN '청약증거금' THEN 'This is a guarantee amount paid when subscribing to public stock or paid-in capital increase.'
        WHEN '균등배정' THEN 'It is a public stock allocation method that allocates the same quantity to subscribers as much as possible.'
        WHEN '비례배정' THEN 'This is a method of allocating stocks in proportion to the subscription margin or application quantity.'
        WHEN '의무보유확약비율' THEN 'It is the ratio of the volume in which institutional investors have pledged not to sell stocks for a certain period of time.'
        WHEN '수요예측' THEN 'This is a procedure to investigate the desired price and demand of institutional investors to determine the public offering price.'
        WHEN '기관투자자' THEN 'This refers to a financial institution or corporate investor who professionally manages funds.'
        WHEN '일반청약' THEN 'This is a process for general investors, such as individuals, to apply for allocation of public offering shares.'
        WHEN '환불일' THEN 'This is the date on which the unallocated margin after subscription is returned to the investor.'
        WHEN '납입일' THEN 'This is the final payment date for the allocated shares in a public offering or capital increase.'
        WHEN '상장일' THEN 'This is the date when securities are first traded on the exchange.'
        WHEN '공모가' THEN 'This is the price per share confirmed to be sold to investors in the public offering process.'
        WHEN '희망공모가밴드' THEN 'This is the expected price range presented to determine the public offering price.'
        WHEN '확정공모가' THEN 'This is the final public offering price determined through demand forecasting, etc.'
        WHEN '청약경쟁률' THEN 'It is a ratio that shows how much the subscription quantity or amount is compared to the available quantity for allocation.'
        WHEN '청약한도' THEN 'This is the maximum subscription quantity that can be applied for by investor type or preferential conditions.'
        WHEN '우대청약한도' THEN 'This is a higher subscription limit applied to investors who meet preferential conditions such as transaction performance.'
        WHEN '배정수량' THEN 'This is the number of shares actually allocated to investors as a result of subscription or paid-in capital increase.'
        WHEN '실권주청약' THEN 'This is a process by which other investors subscribe to new shares that have not been acquired by existing shareholders.'
        WHEN '유상청약' THEN 'This is the process of paying the price and applying to be allocated new shares for paid-in capital increase.'
        WHEN '신주인수권증서' THEN 'It is a security that expresses the right to acquire new shares through paid-in capital increase.'
        WHEN '권리매도' THEN 'This is a transaction that sells new stock warrants or proprietary assets.'
        WHEN '권리매수' THEN 'This is a transaction to purchase rights-based assets such as new stock warrants.'
        WHEN '배당재투자' THEN 'This is a method of investing the dividends paid back into the same asset or portfolio.'
        WHEN '자동투자' THEN 'This is a function that automatically executes investment orders according to set conditions or cycles.'
        WHEN '적립식투자' THEN 'This is a method of investing a certain amount of money on a regular basis and spreading out the purchase timing.'
        WHEN '정액매수' THEN 'It is an investment method in which assets are purchased for the same amount each time.'
        WHEN '소수점투자' THEN 'It is an investment method that involves trading or holding stocks in units of less than one share.'
        WHEN '해외소수점주식' THEN 'This is a service for trading or holding overseas stocks in units of less than one week.'
        WHEN '주식모으기' THEN 'It is an accumulation service that automatically purchases specific stocks at a set period and amount.'
        WHEN '로보어드바이저' THEN 'It is a service where an algorithm proposes or operates a portfolio based on investor tendencies and market information.'
        WHEN '랩서비스' THEN 'It is a comprehensive asset management service where a financial company manages assets on an investor account basis.'
        WHEN '일임형랩' THEN 'It is a wrap service in which investors delegate management and financial companies manage the portfolio.'
        WHEN '자문형랩' THEN 'It is a wrap service that manages accounts based on investment advice.'
        WHEN 'MP' THEN 'It is a management portfolio presented by standardizing the proportion of asset classes or stocks.'
        WHEN '위험등급' THEN 'It is a grade that indicates the possibility of principal loss and volatility of a financial product.'
        WHEN '투자권유준칙' THEN 'This is an internal standard that financial companies must follow when recommending products to investors.'
        WHEN '적합성보고서' THEN 'This is a document that explains the investor’s tendencies and whether the recommended product is suitable.'
        WHEN '핵심설명서' THEN 'This is a manual that summarizes the main risks and costs of the product that investors should be aware of.'
        WHEN '간이투자설명서' THEN 'This is an investment prospectus that briefly summarizes the key information of collective investment securities.'
        WHEN '집합투자규약' THEN 'These are the rules that determine how the fund is operated and investor rights and obligations.'
        WHEN '펀드보수' THEN 'This is the fee borne by the investor for fund operation and sales management.'
        WHEN '판매수수료' THEN 'This is a commission paid to the selling company when selling a financial product.'
        WHEN '순수익지수' THEN 'It is an index that reflects the performance of reinvestment after deducting taxes, etc. from cash flows such as dividends.'
        WHEN '총수익지수' THEN 'It is an index that reflects the overall performance of reinvesting cash flows, including dividends.'
        WHEN '가격지수' THEN 'It is an index that reflects only price changes, excluding cash flows such as dividends.'
        WHEN '환헤지비용' THEN 'This is a cost incurred from hedging transactions to reduce exchange rate risk.'
        WHEN '분배락' THEN 'This is a phenomenon in which the price is adjusted to reflect the impact after distribution rights are confirmed.'
        WHEN '괴리율확대' THEN 'The difference between the market price and the index value or net asset value is increasing.'
        WHEN '상장폐지위험' THEN 'There is a possibility that exchange listings may be abolished due to failure to meet listing maintenance requirements.'
        WHEN '기초지수방법론' THEN 'It is a document or rule that establishes the method of selecting index constituents and rebalancing weight calculation.'
        WHEN '지수산출기관' THEN 'It is an organization that creates and announces indices.'
        WHEN '정기리밸런싱' THEN 'This is a procedure to adjust the composition and weight of an index or portfolio at regular intervals.'
        WHEN '지수라이선스' THEN 'This is the right to use the index for products or services.'
        WHEN '금융소득종합과세' THEN 'This is a system in which financial income, such as interest and dividends, is taxed jointly with other income if it exceeds a certain standard.'
        WHEN '배당소득세' THEN 'This is a tax levied on income received as dividends.'
        WHEN '양도소득세' THEN 'This is a tax levied on capital gains made from selling assets.'
        WHEN '증권거래세' THEN 'This is a tax levied on the transaction amount when selling securities such as stocks.'
        WHEN '농어촌특별세' THEN 'It is a purpose tax added to some financial investment transactions or taxes.'
        WHEN '원천징수' THEN 'This is a system in which the person paying the income deducts and pays taxes in advance.'
        WHEN '원천징수세율' THEN 'This is the tax rate applied when withholding tax.'
        WHEN '절세계좌' THEN 'This is an account that allows you to receive tax benefits such as tax deductions, tax exemption, and tax deferral.'
        WHEN '과세이연' THEN 'This is a tax effect that postpones the payment of taxes to the future.'
        WHEN '손익통산' THEN 'This is a method of calculating taxable income by adding up profits and losses.'
        WHEN '이월공제' THEN 'This is a system in which losses or amounts not deducted in the current year are carried over to subsequent years for deduction.'
        WHEN '기본공제' THEN 'This is basically a deduction amount subtracted from the taxable amount.'
        WHEN '분리과세' THEN 'This is a method of taxing specific income at a separate tax rate rather than combining it with other income.'
        WHEN '비과세' THEN 'If certain requirements under the tax law are met, no tax is levied.'
        WHEN '저율과세' THEN 'This is a taxation method that applies a lower tax rate than the general tax rate.'
        WHEN 'ISA만기' THEN 'This is the end of the mandatory subscription period or contract period for the ISA account.'
        WHEN '연금저축세액공제' THEN 'This is a system that deducts tax on a certain limit of pension savings contributions.'
        WHEN '연금소득세' THEN 'This is a tax levied on income received in the form of pension.'
        WHEN '기타소득세' THEN 'This is a tax levied on temporary other income.'
        WHEN '퇴직소득세' THEN 'This is a tax applied when receiving retirement benefits.'
        WHEN '매출채권팩토링' THEN 'It is a financing method in which trade receivables are transferred to a financial company and converted into cash at an early stage.'
        WHEN '선급비용' THEN 'It is an asset item that has already been paid but still has time left to recognize it as an expense.'
        WHEN '선수수익' THEN 'This is a liability item that has not yet been recognized as revenue among amounts already received.'
        WHEN '단기금융상품' THEN 'It is a deposit-type financial product with a short maturity and relatively easy conversion into cash.'
        WHEN '장기금융상품' THEN 'This refers to a financial product that will be held for more than one year.'
        WHEN '유동성리스부채' THEN 'This item classifies lease liabilities payable within one year as current liabilities.'
        WHEN '비유동리스부채' THEN 'This item classifies lease liabilities payable after one year as non-current liabilities.'
        WHEN '미청구공사' THEN 'Revenue has been recognized as construction progresses, but has not yet been billed.'
        WHEN '초과청구공사' THEN 'This amount is recognized as a liability because more is charged than the progress of the construction.'
        WHEN '공사손실충당부채' THEN 'It is a provision recognized in preparation for expected losses in a construction contract.'
        WHEN '매출원가율' THEN 'It shows the level of cost burden as a ratio of cost of sales to sales.'
        WHEN '판관비율' THEN 'It is used to judge cost efficiency based on the ratio of selling and administrative expenses to sales.'
        WHEN '인건비율' THEN 'It represents the ratio of labor costs to sales or total costs.'
        WHEN '감가상각률' THEN 'It is the ratio of depreciation cost to the book value or acquisition cost of tangible assets.'
        WHEN 'NIM' THEN 'This is the margin rate that financial companies such as banks earn from the difference between interest income and interest expenses.'
        WHEN '대손비용률' THEN 'It is the ratio of credit loss costs, such as provision for loan losses, to loans or sales.'
        WHEN '연체율' THEN 'It is the ratio of the amount of loans or bonds that have exceeded the agreed upon deadline.'
        WHEN '고정이하여신비율' THEN 'This is the ratio of potentially non-performing loans classified as substandard or lower among financial company loans.'
        WHEN '충당금적립률' THEN 'It is the ratio of provisions accumulated compared to potentially non-performing loans.'
        WHEN 'SIGNAL DRIFT' THEN 'This is a phenomenon in which the distribution or direction of model signals changes from past standards.'
        WHEN 'MODEL DRIFT' THEN 'This is a phenomenon in which the predictive performance or data relationships of an operating model change over time.'
        WHEN 'DATA LATENCY' THEN 'This is the delay time between data generation and system reflection.'
        WHEN 'MISSING VALUE RATE' THEN 'This is the proportion of missing values ​​among the total data.'
        WHEN 'FEATURE LAG' THEN 'This refers to the delay until the input variable becomes actually available.'
        WHEN 'LOOKAHEAD BIAS' THEN 'It is a bias that incorrectly uses information that can only be known in the future to analyze the past.'
        WHEN 'SURVIVORSHIP BIAS' THEN 'It is a bias that only analyzes those that are currently surviving, leaving out cases of past failure.'
        WHEN 'SELECTION BIAS' THEN 'The sample selection process is a bias that distorts the results.'
        WHEN 'REBALANCE DATE' THEN 'This is the date on which changes to portfolio or index composition are implemented.'
        WHEN 'EFFECTIVE DATE' THEN 'This is the date when announcement events or index changes take actual effect.'
        WHEN 'TRADE DATE' THEN 'This is the date on which the sales transaction was concluded.'
        WHEN 'SETTLEMENT DATE' THEN 'This is the date when the transaction amount and securities are actually settled.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '오차항',
        '잔차변동성',
        '베타노출',
        '전진분석',
        '알파감쇠',
        '신호반감기',
        '체결확률',
        '세금효과',
        '체결가정',
        '매매빈도',
        '보유기간수익률',
        '초과성과',
        '해외주식',
        '미국주식',
        '미국정규장',
        '미국프리마켓',
        '미국애프터마켓',
        '야간거래',
        '주간거래',
        '환전우대',
        '통합증거금',
        '외화증거금',
        '원화증거금',
        '예수금',
        '주문가능금액',
        '출금가능금액',
        '매수가능금액',
        '미수거래',
        '미수동결',
        '반대매매예정',
        '담보대출',
        '주식담보대출',
        '신용공여',
        '신용매수',
        '신용상환',
        '융자잔고',
        '대주잔고',
        '연체이자',
        '권리정보',
        '배당입금',
        '배당락일',
        '지급일',
        '기준통화',
        '결제통화',
        '원화환산금액',
        '평가금액',
        '평가손익',
        '실현손익',
        '미실현손익',
        '평균단가',
        '매입단가',
        '수익률알림',
        '조건검색',
        '자동주문',
        '예약주문',
        '스탑주문',
        '트레일링스탑',
        'OCO주문',
        'Bracket주문',
        'LOC',
        'MOC',
        'VWAP주문',
        'TWAP주문',
        '빙산주문',
        '분할주문',
        '주문유효기간',
        '당일주문',
        'GTC',
        'GTD',
        '부분체결',
        '미체결주문',
        '체결내역',
        '정정주문',
        '취소주문',
        '잔량취소',
        '주문거부',
        '주문접수',
        '주문번호',
        '원장잔고',
        '실시간잔고',
        '체결기준잔고',
        '결제기준잔고',
        '평가손익률',
        '발행어음',
        '외화RP',
        '달러RP',
        'MMDA',
        'ISA중개형',
        'ISA신탁형',
        'ISA일임형',
        '연금저축펀드',
        '연금저축보험',
        '퇴직연금ETF',
        'TDF빈티지',
        'TIF',
        '공모주청약',
        '청약증거금',
        '균등배정',
        '비례배정',
        '의무보유확약비율',
        '수요예측',
        '기관투자자',
        '일반청약',
        '환불일',
        '납입일',
        '상장일',
        '공모가',
        '희망공모가밴드',
        '확정공모가',
        '청약경쟁률',
        '청약한도',
        '우대청약한도',
        '배정수량',
        '실권주청약',
        '유상청약',
        '신주인수권증서',
        '권리매도',
        '권리매수',
        '배당재투자',
        '자동투자',
        '적립식투자',
        '정액매수',
        '소수점투자',
        '해외소수점주식',
        '주식모으기',
        '로보어드바이저',
        '랩서비스',
        '일임형랩',
        '자문형랩',
        'MP',
        '위험등급',
        '투자권유준칙',
        '적합성보고서',
        '핵심설명서',
        '간이투자설명서',
        '집합투자규약',
        '펀드보수',
        '판매수수료',
        '순수익지수',
        '총수익지수',
        '가격지수',
        '환헤지비용',
        '분배락',
        '괴리율확대',
        '상장폐지위험',
        '기초지수방법론',
        '지수산출기관',
        '정기리밸런싱',
        '지수라이선스',
        '금융소득종합과세',
        '배당소득세',
        '양도소득세',
        '증권거래세',
        '농어촌특별세',
        '원천징수',
        '원천징수세율',
        '절세계좌',
        '과세이연',
        '손익통산',
        '이월공제',
        '기본공제',
        '분리과세',
        '비과세',
        '저율과세',
        'ISA만기',
        '연금저축세액공제',
        '연금소득세',
        '기타소득세',
        '퇴직소득세',
        '매출채권팩토링',
        '선급비용',
        '선수수익',
        '단기금융상품',
        '장기금융상품',
        '유동성리스부채',
        '비유동리스부채',
        '미청구공사',
        '초과청구공사',
        '공사손실충당부채',
        '매출원가율',
        '판관비율',
        '인건비율',
        '감가상각률',
        'NIM',
        '대손비용률',
        '연체율',
        '고정이하여신비율',
        '충당금적립률',
        'SIGNAL DRIFT',
        'MODEL DRIFT',
        'DATA LATENCY',
        'MISSING VALUE RATE',
        'FEATURE LAG',
        'LOOKAHEAD BIAS',
        'SURVIVORSHIP BIAS',
        'SELECTION BIAS',
        'REBALANCE DATE',
        'EFFECTIVE DATE',
        'TRADE DATE',
        'SETTLEMENT DATE'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN 'AS_OF_DATE' THEN 'This is a date that indicates the current status of the data.'
        WHEN 'SNAPSHOT DATE' THEN 'This is the date when snapshot data was saved or observed.'
        WHEN 'POINT-IN-TIME DATA' THEN 'This is data that preserves only the information that was actually known at a specific point in time.'
        WHEN 'BACKFILL' THEN 'This is a process of belatedly filling in data in the past section.'
        WHEN 'UNIVERSE FILTER' THEN 'This is a rule that excludes items that do not meet the conditions from the stock group being analyzed.'
        WHEN 'LIQUIDITY FILTER' THEN 'This is a rule that filters out targets based on liquidity criteria such as transaction amount or quotation conditions.'
        WHEN 'EVENT TAG' THEN 'This is a tag that displays the type of event such as public news, price change, etc.'
        WHEN 'NEWS SENTIMENT' THEN 'It is a signal that quantifies the positive, negative, and neutral degree of a news sentence.'
        WHEN 'DISCLOSURE SENTIMENT' THEN 'This is a positive, negative, and neutral tendency signal extracted from the disclosure content.'
        WHEN 'RISK FLAG' THEN 'This is a flag that indicates that a risk factor has been detected in the analysis target.'
        WHEN 'ANOMALY FLAG' THEN 'This is a flag that indicates that an abnormal pattern different from usual has been detected.'
        WHEN 'VALIDATION RULE' THEN 'This is a verification rule that checks whether data or model results meet standards.'
        WHEN 'DATA LINEAGE' THEN 'It refers to the flow and source history in which data is created, transformed, and stored.'
        WHEN 'SOURCE PRIORITY' THEN 'This is a ranking that determines which value among several data sources will be used first.'
        WHEN 'ALIAS HIT' THEN 'This is a search result where the search term matches an alias, not a canonical term.'
        WHEN 'CANONICAL HIT' THEN 'These are search results where the search term directly matches the canonical term.'
        WHEN 'SEARCH RANKING' THEN 'It is a scoring method that determines the order of exposure of search results.'
        WHEN 'EXACT MATCH' THEN 'This is a condition where the search term and target string exactly match.'
        WHEN 'PREFIX MATCH' THEN 'The condition is that the search term matches the first part of the target string.'
        WHEN 'CONTAINS MATCH' THEN 'The condition is that the search term is included in the target string.'
        WHEN 'TERM COVERAGE' THEN 'This is the ratio of terms registered in the dictionary among terms requiring service.'
        WHEN '액티브셰어' THEN 'It is an indicator of how much the portfolio holdings differ from the benchmark.'
        WHEN '트래킹디퍼런스' THEN 'This is the actual difference between the ETF or portfolio return rate and the reference index return rate.'
        WHEN '오메가비율' THEN 'It is a performance indicator that compares the profit distribution above the target return rate with the loss distribution below it.'
        WHEN '울서지수' THEN 'It is a downside risk indicator that reflects both the size and duration of the decline.'
        WHEN '페인지수' THEN 'It is an indicator that measures the perceived loss by reflecting the decline and period experienced by the investor.'
        WHEN '왜도' THEN 'The degree to which the return distribution is skewed to one side relative to the average.'
        WHEN '첨도' THEN 'It is a statistical quantity that represents the tail thickness and central concentration of the return distribution.'
        WHEN '꼬리위험' THEN 'There is a risk of large losses occurring in the extreme sections of the distribution.'
        WHEN '낙폭기간' THEN 'It is a period of loss after the peak.'
        WHEN '회복계수' THEN 'It is an indicator of loss recovery efficiency by comparing the rate of return to the maximum drop.'
        WHEN '이익확률' THEN 'It is the likelihood that a transaction or strategy will result in a profit.'
        WHEN '손실확률' THEN 'The possibility that a trade or strategy will result in a loss.'
        WHEN '평균이익' THEN 'It is the average value of profit generated from profit trading.'
        WHEN '평균손실' THEN 'It is the average value of losses incurred in loss transactions.'
        WHEN '최대연속손실' THEN 'The maximum number or period of consecutive loss-making transactions.'
        WHEN '최대연속이익' THEN 'This is the maximum number or period of consecutive profit transactions.'
        WHEN '위험패리티' THEN 'It is a portfolio construction method that matches the risk contribution of each asset similarly.'
        WHEN '최소분산포트폴리오' THEN 'It is a portfolio constructed to have the lowest volatility in a given asset class.'
        WHEN '효율적프론티어' THEN 'The set of portfolios that offers the highest return for a given risk level or the lowest risk for a given return level.'
        WHEN '최적화제약' THEN 'In portfolio optimization, it is a condition that must be observed, such as a weight limit or trading limit.'
        WHEN '롱온리' THEN 'It is an investment method that holds only long positions without short selling.'
        WHEN '롱숏' THEN 'It is an investment method that buys assets expected to rise and sells assets expected to decline.'
        WHEN '시장중립' THEN 'It is a strategy that reduces the impact of the overall market direction and aims for the relative value or stock selection effect.'
        WHEN '리스크온' THEN 'The market atmosphere is such that investors are increasing their preference for risky assets.'
        WHEN '리스크오프' THEN 'The market atmosphere is such that investors are reducing risky assets and preferring safe assets.'
        WHEN '금리민감도' THEN 'This is the extent to which asset prices or profits change in response to changes in interest rates.'
        WHEN '크레딧민감도' THEN 'This is the extent to which asset values ​​respond to changes in credit spreads.'
        WHEN '옵션민감도' THEN 'This is the degree to which option prices respond to changes in the underlying asset price, interest rate volatility, and time.'
        WHEN '듀레이션갭' THEN 'It represents interest rate risk exposure as the difference in duration between assets and liabilities.'
        WHEN '볼록성효과' THEN 'This is the effect of bond prices moving more or less linearly when the interest rate change is large.'
        WHEN '환산수익률' THEN 'It is the value of returns based on different standards converted to the same period or currency standard.'
        WHEN '기여수익률' THEN 'It is the percentage that a specific component contributes to the overall portfolio return.'
        WHEN '분산효과' THEN 'This is the effect of partially offsetting individual risks by holding multiple assets together.'
        WHEN '집중위험' THEN 'This is a risk that arises due to overweighting of asset groups in specific stocks and industries.'
        WHEN '한도소진율' THEN 'This is the percentage of the set risk or investment limit that has already been used.'
        WHEN '매출성장률' THEN 'It is a ratio that indicates how much sales increased compared to the previous year or the previous year.'
        WHEN '영업이익성장률' THEN 'It is a ratio that indicates how much operating profit has increased compared to the comparative period.'
        WHEN '순이익성장률' THEN 'It is a ratio that indicates how much net profit has increased compared to the comparative period.'
        WHEN '총자산증가율' THEN 'It is a ratio that indicates how much the total assets have increased compared to the comparative period.'
        WHEN '자본증가율' THEN 'It is a ratio that indicates how much total capital has increased compared to the comparative period.'
        WHEN '부채증가율' THEN 'It is a ratio that indicates how much total debt has increased compared to the comparative period.'
        WHEN '재고증가율' THEN 'It is a ratio that indicates how much inventory assets have increased compared to the comparative period.'
        WHEN '매출채권증가율' THEN 'It is a ratio that indicates how much sales receivables have increased compared to the comparative period.'
        WHEN '영업현금흐름마진' THEN 'It is a ratio that compares operating cash flow to sales to determine cash generation ability.'
        WHEN '현금비율' THEN 'It is a ratio that compares cash and cash equivalents to current liabilities to determine short-term solvency.'
        WHEN '순부채비율' THEN 'It is a ratio that measures financial leverage by comparing net borrowings to capital or assets.'
        WHEN '차입금의존도' THEN 'It is the proportion of borrowings among total assets or capital raising.'
        WHEN '금융비용부담률' THEN 'It is a ratio that represents the level of financial cost burden compared to operating profit or sales.'
        WHEN '이자비용' THEN 'This is an expense incurred in exchange for the use of borrowings or financial liabilities.'
        WHEN '이자수익' THEN 'This is the profit earned from interest-bearing financial assets such as deposits, loans, and bonds.'
        WHEN '배당수익' THEN 'This is revenue recognized as dividends received from stocks or shares held.'
        WHEN '외환차익' THEN 'It is profit generated from foreign currency transactions or settlement of foreign currency assets and liabilities.'
        WHEN '외환차손' THEN 'This is a loss arising from foreign currency transactions or settlement of foreign currency assets and liabilities.'
        WHEN '외화환산이익' THEN 'This is a profit generated from exchange rate fluctuations when converting foreign currency assets and liabilities.'
        WHEN '외화환산손실' THEN 'This is a loss incurred due to exchange rate fluctuations when converting foreign currency assets and liabilities.'
        WHEN '파생상품평가이익' THEN 'This is valuation profit arising from changes in the fair value of derivative products.'
        WHEN '파생상품평가손실' THEN 'This is a valuation loss arising from changes in the fair value of derivatives.'
        WHEN '리스료' THEN 'This is the amount paid in exchange for the use of the right-of-use asset according to the lease contract.'
        WHEN '리스이자비용' THEN 'This is interest expense recognized by applying the effective interest rate to the lease liability.'
        WHEN '감가상각대상금액' THEN 'This is the amount subject to depreciation calculation by subtracting the residual value from the asset acquisition cost.'
        WHEN '잔존가치' THEN 'This is the amount expected to be recovered at the end of use of the asset.'
        WHEN '회수가능액' THEN 'The larger amount between the asset''s value in use and its fair value less costs to sell is used to determine impairment.'
        WHEN '사용가치' THEN 'This is the amount converted into present value of the future cash flow that will be obtained from the use of the asset.'
        WHEN '내용연수' THEN 'This is the period during which the asset is expected to be used economically.'
        WHEN '자산손상' THEN 'A loss is recognized when the carrying amount of an asset is greater than its recoverable amount.'
        WHEN '통화안정증권' THEN 'These are short-term debt securities issued by the Bank of Korea to control liquidity.'
        WHEN '금융중개지원대출' THEN 'This is a loan system provided by the Bank of Korea to support financial institutions’ loans to small and medium-sized businesses.'
        WHEN '지준시장' THEN 'It is a short-term money market in which financial institutions adjust reserve reserves or margins.'
        WHEN '초단기금리' THEN 'This is the interest rate applied to fund transactions for one day or a very short period of time.'
        WHEN 'CD금리' THEN 'This is the short-term market interest rate formed in certificate of deposit transactions.'
        WHEN 'CP금리' THEN 'This is the short-term funding interest rate formed in commercial paper transactions.'
        WHEN '은행채금리' THEN 'It is the market rate of return on bonds issued by banks.'
        WHEN '국채선물' THEN 'It is a futures product traded in conjunction with changes in government bond prices or yields.'
        WHEN '외환당국' THEN 'This refers to institutions such as the government and central bank that are responsible for stabilizing the foreign exchange market and foreign exchange policy.'
        WHEN '환율변동성' THEN 'The exchange rate fluctuates for a certain period of time.'
        WHEN '외환포지션' THEN 'Net exposure to foreign currency assets and liabilities or buy and sell positions.'
        WHEN '선물환' THEN 'It is a transaction to buy and sell foreign currency at a set exchange rate at a certain point in the future.'
        WHEN 'NDF' THEN 'It is an offshore forward exchange transaction in which only the difference is settled without delivery of the principal.'
        WHEN '외국환평형기금채권' THEN 'These are bonds issued to provide financial resources to stabilize the foreign exchange market.'
        WHEN '대외건전성' THEN 'It is the stability that allows a country or financial system to withstand external shocks.'
        WHEN '자본유출' THEN 'This is a phenomenon in which domestic funds move to overseas assets or markets.'
        WHEN '자본유입' THEN 'This is a phenomenon in which foreign funds flow into domestic assets or markets.'
        WHEN 'LCR' THEN 'This is the ratio of high liquid assets that a bank can withstand in a short-term liquidity crisis.'
        WHEN 'NSFR' THEN 'It is a liquidity regulation indicator that evaluates a bank''s long-term stable funding level.'
        WHEN '바젤III' THEN 'It is an international financial regulatory system that strengthens bank capital and liquidity regulations.'
        WHEN '경기확산지수' THEN 'Among various economic indicators, the proportion of indicators that are improving indicates the degree of economic expansion.'
        WHEN '소비자심리지수' THEN 'It is a psychological indicator that indexes consumers’ perception and outlook of the economic situation.'
        WHEN 'BSI' THEN 'It is an index created by examining the economic situation and outlook as perceived by companies.'
        WHEN 'ESI' THEN 'It is an index that is used to judge the economy by combining corporate and consumer sentiment.'
        WHEN '수출입물량지수' THEN 'It is an index that separates changes in the quantity of imported and exported products from price changes.'
        WHEN '교역조건지수' THEN 'It is an indicator that indexes the relative changes in export and import prices.'
        WHEN 'GNI' THEN 'It is an indicator that combines all the income earned by citizens at home and abroad.'
        WHEN '가처분소득' THEN 'It is income that can be used freely after subtracting taxes and social contributions from income.'
        WHEN '소득분배지표' THEN 'It is a statistical indicator that shows how income is divided between classes.'
        WHEN '지니계수' THEN 'It is an indicator that represents the degree of income distribution inequality as a value between 0 and 1.'
        WHEN '상대적빈곤율' THEN 'It is the proportion of the population with an income lower than a certain percentage of the median income.'
        WHEN '고령화율' THEN 'This is the proportion of the elderly population out of the total population.'
        WHEN '생산가능인구' THEN 'This refers to the population of an age group capable of economic activity.'
        WHEN '부양비' THEN 'It is the ratio of the youth and elderly population that the working-age population must support.'
        WHEN '잠재노동력' THEN 'This is a labor force that has the potential to participate in the labor market but is currently not fully utilized.'
        WHEN '자산양수도' THEN 'It refers to a transaction in which a company buys or sells important assets.'
        WHEN '채무보증결정' THEN 'It is a decision by a company to provide a guarantee for another person''s debt.'
        WHEN '담보제공결정' THEN 'This is the company''s decision to provide assets as collateral.'
        WHEN '단일판매공급계약해지' THEN 'A significant single sales or supply contract has been terminated.'
        WHEN '주식교환이전결정' THEN 'It is a decision to reorganize the governance structure through stock exchange or stock transfer.'
        WHEN '회생절차개시신청' THEN 'It is true that the company has applied to the court to initiate rehabilitation procedures.'
        WHEN '상장예비심사' THEN 'This is a process in which the exchange pre-examines whether listing requirements are met before applying for listing.'
        WHEN '증권발행실적보고서' THEN 'It is a public disclosure document that reports sales performance after the issuance of securities.'
        WHEN '투자위험요소' THEN 'This is a risk factor that investors should keep in mind in the securities report or investment prospectus.'
        WHEN '자금사용목적' THEN 'This item explains where the funds raised through public offering or capital increase will be used.'
        WHEN '보호예수기간' THEN 'This is a period set during which stocks subject to sales restrictions cannot be disposed of.'
        WHEN '의무보유기간' THEN 'It is a period set during which a certain amount cannot be sold after listing or public offering.'
        WHEN '임원현황' THEN 'This is a disclosure item that explains the company''s registered executives and the composition of key executives.'
        WHEN '사업의내용' THEN 'This is a disclosure item that explains the current status of the company''s major business products and services market.'
        WHEN '연결대상종속회사' THEN 'It is a subsidiary of the parent company included in the consolidated financial statements.'
        WHEN '핵심감사사항' THEN 'These are matters that the auditor determines are most important in the audit and are included in the audit report.'
        WHEN '감사인의강조사항' THEN 'This is something the auditor emphasized in the audit report to draw user attention.'
        WHEN '내부회계비적정' THEN 'There are opinions or review results that are not appropriate regarding the internal accounting management system.'
        WHEN '공시번복' THEN 'This is an act that undermines market trust by overturning or canceling information that has already been announced.'
        WHEN '공시변경' THEN 'This is the act of later changing major information that has already been announced.'
        WHEN '공시불이행' THEN 'The listed company has not fulfilled its prescribed disclosure obligations.'
        WHEN '공시벌점' THEN 'This is a penalty point imposed by the exchange for violations of disclosure, such as insincere disclosure.'
        WHEN '상장적격성심사개시' THEN 'The exchange has begun reviewing eligibility to maintain listing.'
        WHEN '개선기간' THEN 'This is a period granted to restore listing maintenance requirements or implement improvement plans.'
        WHEN '개선계획서' THEN 'This is a plan submitted by a company to maintain listing or improve internal control.'
        WHEN '심사대상결정' THEN 'The exchange has decided to subject it to listing eligibility review.'
        WHEN '상장폐지결정' THEN 'The exchange has decided to delist rather than allow the listing to be maintained.'
        WHEN '이의신청' THEN 'This is a procedure in which a company appeals against a decision on delisting or sanctions and requests a retrial.'
        WHEN '매매정지사유' THEN 'Disclosure system financial or other reasons that led to the suspension of trading.'
        WHEN '대량매매' THEN 'It is a transaction method that separates large quantities from general transactions.'
        WHEN '블록딜' THEN 'It is a method of trading large quantities of stocks in bulk with a designated counterparty both on and off the market.'
        WHEN '시간외대량매매' THEN 'It is a method of trading large quantities in the after-hours market outside of the regular market.'
        WHEN '장중대량매매' THEN 'This is a method of trading large quantities under separate conditions during regular trading hours.'
        WHEN '바스켓매매' THEN 'It is a trading method where multiple stocks are bought and sold simultaneously as a bundle.'
        WHEN '프로그램호가' THEN 'This is a bid or sell price submitted through program trading.'
        WHEN '차익잔고' THEN 'This is the remaining balance of the arbitrage position that has not yet been liquidated.'
        WHEN '비차익매수' THEN 'It is a program trading that buys a basket of stocks without being linked to futures.'
        WHEN '비차익매도' THEN 'It is a program trading that sells a basket of stocks without being linked to futures.'
        WHEN '선물스프레드' THEN 'It is a trading strategy or price difference between futures contracts with different expirations.'
        WHEN '롤오버수요' THEN 'This is a transaction demand to transfer a derivatives position that is nearing maturity to the next maturity.'
        WHEN '만기효과' THEN 'This is the price supply and demand effect that appears in the spot and derivative markets around the futures option expiration date.'
        WHEN '동시만기일' THEN 'The maturity date of several derivative products, such as stock index futures and options, is on the same day.'
        WHEN '네마녀의날' THEN 'It is a day when volatility and trading volume can increase due to the overlap of multiple derivative product expirations.'
        WHEN '시장경보제도' THEN 'It is a system in which the exchange provides step-by-step warnings about speculative transactions or unusual sudden fluctuations.'
        WHEN '투자위험예고' THEN 'This is a measure to notify in advance that the possibility of being designated as an investment risk item has increased.'
        WHEN '단기과열예고' THEN 'This is a measure to notify in advance the possibility of designation as a short-term overheated stock.'
        WHEN '공매도잔고보고' THEN 'It is an obligation for investors holding short selling balances above a certain standard to report.'
        WHEN '공매도잔고공시' THEN 'This is a system that is disclosed to the market when the short selling balance exceeds a certain standard.'
        WHEN '업틱룰예외' THEN 'This is an exception where the short selling price limit rule does not apply in certain situations.'
        WHEN '매수호가잔량' THEN 'This is the quantity of buy orders remaining that have not yet been filled at a specific price range.'
        WHEN '매도호가잔량' THEN 'This is the quantity of sell orders remaining that have not yet been filled at a specific price range.'
        WHEN '호가가격단위' THEN 'This is the minimum order price change unit applied for each price range.'
        WHEN '주문수량단위' THEN 'This is the minimum quantity that can be ordered for each exchange or product.'
        WHEN '최소주문수량' THEN 'This is the smallest order quantity required for an order to be accepted.'
        WHEN '최대주문수량' THEN 'This is the upper limit on the number of orders that can be submitted at one time.'
        WHEN '거래단위' THEN 'It is the standard quantity of one contract or one order in a product or market.'
        WHEN '매매수량단위' THEN 'It is the minimum quantity unit that can be sold.'
        WHEN '가격변동단위' THEN 'It is the smallest unit by which product prices can move.'
        WHEN 'ELF' THEN 'This refers to a fund-type product that mainly invests in ELS.'
        WHEN 'ELD' THEN 'It is a deposit-type product whose profits are determined by linking it to stock indexes, etc.'
        WHEN 'DLF' THEN 'It is a fund-type product that invests in derivatives-linked securities.'
        WHEN 'DLT' THEN 'It is a product that contains the structure of derivatives-linked securities in the form of a trust.'
        WHEN '채권혼합형펀드' THEN 'It is a mixed fund that focuses on bonds and also includes some stocks.'
        WHEN '주식혼합형펀드' THEN 'It is a mixed fund that focuses on stocks and includes other assets such as bonds.'
        WHEN 'MMW' THEN 'It is a cash management product operated in short-term financial products with a securities company wrap structure.'
        WHEN '종합매매계좌' THEN 'It is a securities account that allows trading in various financial products such as stocks, bonds, and funds.'
        WHEN '위탁계좌' THEN 'This is an account opened by an investor to entrust the purchase and sale of securities.'
        WHEN '비대면계좌' THEN 'It is a financial investment account that can be opened online without visiting a branch.'
        WHEN 'CMA-RP형' THEN 'This is a type in which CMA funds are mainly managed in RP.'
        WHEN 'CMA-MMF형' THEN 'This is a type in which CMA funds are mainly managed in MMFs.'
        WHEN '중개형ISA이전' THEN 'This is an account transfer procedure to transfer an existing ISA to a brokerage ISA.'
        WHEN '연금이전' THEN 'This is the process of transferring pension savings or retirement pension accounts to another financial company.'
        WHEN '퇴직연금디폴트옵션' THEN 'This is a system that operates with a pre-determined product when the subscriber does not give operation instructions.'
        WHEN '사전지정운용제도' THEN 'This is a system that determines in advance the product to be applied in retirement pension when there is no operation instruction.'
        WHEN '디폴트옵션상품' THEN 'This is a management product that can be designated as a retirement pension default option.'
        WHEN '원리금보장형' THEN 'It is a type of financial product in which principal and interest are guaranteed according to the terms of the agreement.'
        WHEN '실적배당형' THEN 'This is a type of product in which profits or losses are attributed to the investor depending on management performance.'
        WHEN '예금자보호' THEN 'It is a system that protects up to a certain limit when deposits are not returned due to bankruptcy of a financial company.'
        WHEN '예금보험공사' THEN 'It is an organization that operates the depositor protection system and is responsible for liquidating insolvent financial companies.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        'AS_OF_DATE',
        'SNAPSHOT DATE',
        'POINT-IN-TIME DATA',
        'BACKFILL',
        'UNIVERSE FILTER',
        'LIQUIDITY FILTER',
        'EVENT TAG',
        'NEWS SENTIMENT',
        'DISCLOSURE SENTIMENT',
        'RISK FLAG',
        'ANOMALY FLAG',
        'VALIDATION RULE',
        'DATA LINEAGE',
        'SOURCE PRIORITY',
        'ALIAS HIT',
        'CANONICAL HIT',
        'SEARCH RANKING',
        'EXACT MATCH',
        'PREFIX MATCH',
        'CONTAINS MATCH',
        'TERM COVERAGE',
        '액티브셰어',
        '트래킹디퍼런스',
        '오메가비율',
        '울서지수',
        '페인지수',
        '왜도',
        '첨도',
        '꼬리위험',
        '낙폭기간',
        '회복계수',
        '이익확률',
        '손실확률',
        '평균이익',
        '평균손실',
        '최대연속손실',
        '최대연속이익',
        '위험패리티',
        '최소분산포트폴리오',
        '효율적프론티어',
        '최적화제약',
        '롱온리',
        '롱숏',
        '시장중립',
        '리스크온',
        '리스크오프',
        '금리민감도',
        '크레딧민감도',
        '옵션민감도',
        '듀레이션갭',
        '볼록성효과',
        '환산수익률',
        '기여수익률',
        '분산효과',
        '집중위험',
        '한도소진율',
        '매출성장률',
        '영업이익성장률',
        '순이익성장률',
        '총자산증가율',
        '자본증가율',
        '부채증가율',
        '재고증가율',
        '매출채권증가율',
        '영업현금흐름마진',
        '현금비율',
        '순부채비율',
        '차입금의존도',
        '금융비용부담률',
        '이자비용',
        '이자수익',
        '배당수익',
        '외환차익',
        '외환차손',
        '외화환산이익',
        '외화환산손실',
        '파생상품평가이익',
        '파생상품평가손실',
        '리스료',
        '리스이자비용',
        '감가상각대상금액',
        '잔존가치',
        '회수가능액',
        '사용가치',
        '내용연수',
        '자산손상',
        '통화안정증권',
        '금융중개지원대출',
        '지준시장',
        '초단기금리',
        'CD금리',
        'CP금리',
        '은행채금리',
        '국채선물',
        '외환당국',
        '환율변동성',
        '외환포지션',
        '선물환',
        'NDF',
        '외국환평형기금채권',
        '대외건전성',
        '자본유출',
        '자본유입',
        'LCR',
        'NSFR',
        '바젤III',
        '경기확산지수',
        '소비자심리지수',
        'BSI',
        'ESI',
        '수출입물량지수',
        '교역조건지수',
        'GNI',
        '가처분소득',
        '소득분배지표',
        '지니계수',
        '상대적빈곤율',
        '고령화율',
        '생산가능인구',
        '부양비',
        '잠재노동력',
        '자산양수도',
        '채무보증결정',
        '담보제공결정',
        '단일판매공급계약해지',
        '주식교환이전결정',
        '회생절차개시신청',
        '상장예비심사',
        '증권발행실적보고서',
        '투자위험요소',
        '자금사용목적',
        '보호예수기간',
        '의무보유기간',
        '임원현황',
        '사업의내용',
        '연결대상종속회사',
        '핵심감사사항',
        '감사인의강조사항',
        '내부회계비적정',
        '공시번복',
        '공시변경',
        '공시불이행',
        '공시벌점',
        '상장적격성심사개시',
        '개선기간',
        '개선계획서',
        '심사대상결정',
        '상장폐지결정',
        '이의신청',
        '매매정지사유',
        '대량매매',
        '블록딜',
        '시간외대량매매',
        '장중대량매매',
        '바스켓매매',
        '프로그램호가',
        '차익잔고',
        '비차익매수',
        '비차익매도',
        '선물스프레드',
        '롤오버수요',
        '만기효과',
        '동시만기일',
        '네마녀의날',
        '시장경보제도',
        '투자위험예고',
        '단기과열예고',
        '공매도잔고보고',
        '공매도잔고공시',
        '업틱룰예외',
        '매수호가잔량',
        '매도호가잔량',
        '호가가격단위',
        '주문수량단위',
        '최소주문수량',
        '최대주문수량',
        '거래단위',
        '매매수량단위',
        '가격변동단위',
        'ELF',
        'ELD',
        'DLF',
        'DLT',
        '채권혼합형펀드',
        '주식혼합형펀드',
        'MMW',
        '종합매매계좌',
        '위탁계좌',
        '비대면계좌',
        'CMA-RP형',
        'CMA-MMF형',
        '중개형ISA이전',
        '연금이전',
        '퇴직연금디폴트옵션',
        '사전지정운용제도',
        '디폴트옵션상품',
        '원리금보장형',
        '실적배당형',
        '예금자보호',
        '예금보험공사'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '저축성보험' THEN 'It is an insurance product with greater savings and maturity refund functions than risk protection.'
        WHEN '변액보험' THEN 'This is a product in which a portion of the premium is invested in a fund, and the insurance premium fluctuates depending on performance.'
        WHEN '변액연금보험' THEN 'It is a variable insurance-type pension product whose pension resources vary depending on management performance.'
        WHEN '저축은행예금' THEN 'This is a deposit product sold by a savings bank and may be subject to depositor protection.'
        WHEN '파킹통장' THEN 'It is a savings account that allows deposits and withdrawals at any time and offers a relatively high interest rate.'
        WHEN '정기예금' THEN 'It is a deposit product in which you deposit money for a set period of time and receive an agreed interest rate.'
        WHEN '정기적금' THEN 'It is a product in which you pay a certain amount over a set period of time and receive the principal and interest at maturity.'
        WHEN '발행어음형CMA' THEN 'This is a type in which CMA funds are managed in bills issued by securities companies.'
        WHEN '외화예금' THEN 'It is a deposit product that deposits in foreign currency and earns interest.'
        WHEN '달러예금' THEN 'This is a foreign currency deposit deposited in US dollars.'
        WHEN '외화채권' THEN 'It is a bond in which the principal and interest are paid in foreign currency.'
        WHEN '미국국채' THEN 'These are government bonds issued by the U.S. government.'
        WHEN '단기사채' THEN 'Private bonds with a short maturity are used to raise short-term funds for companies.'
        WHEN '전자단기사채' THEN 'It is a short-term private bond issued through electronic registration.'
        WHEN 'ABS' THEN 'It is a security issued to pay principal and interest based on cash flow generated from the underlying asset.'
        WHEN 'MBS' THEN 'These are securities issued based on mortgage assets such as home mortgage loans.'
        WHEN 'CLO' THEN 'It is a security issued by structuring loan receivables as underlying assets.'
        WHEN '커버드본드' THEN 'These are bonds issued by financial companies based on collateral assets, and the issuing institution is also responsible for repayment.'
        WHEN '신종자본증권' THEN 'It is a subordinated security that can be partially recognized as capital in accounting.'
        WHEN '조건부자본증권' THEN 'These are capital securities that are written off or converted into stocks under certain conditions, such as crisis situations.'
        WHEN '코코본드' THEN 'This is a practical expression meaning contingent capital securities.'
        WHEN 'ESG펀드' THEN 'It is a fund that reflects environmental, social and governance factors in investment decisions.'
        WHEN '인컴펀드' THEN 'It is a fund that pursues regular cash flow such as interest, dividends, and rent.'
        WHEN '배당주펀드' THEN 'This fund primarily invests in stocks with high dividend payout ratio or dividend yield.'
        WHEN '커버드콜펀드' THEN 'It is a fund that pursues income by combining holding underlying assets and selling call options.'
        WHEN '타깃인컴펀드' THEN 'It is a fund managed with the goal of withdrawal and cash flow management after retirement.'
        WHEN '초단기채펀드' THEN 'This fund invests in short-maturity bonds and short-term financial products.'
        WHEN '만기매칭형펀드' THEN 'It is a fund that is managed according to the bond maturity and fund maturity.'
        WHEN '만기채권형ETF' THEN 'This is an ETF that manages a bond portfolio until a set maturity.'
        WHEN '월배당ETF' THEN 'This is an ETF operated with the goal of paying monthly distributions.'
        WHEN '주식채권혼합ETF' THEN 'It is an ETF that manages stocks and bonds together.'
        WHEN '멀티에셋ETF' THEN 'It is an ETF designed to diversify investments across multiple asset classes.'
        WHEN '액티브채권ETF' THEN 'It is an active ETF that forms a bond portfolio based on the operator’s judgment.'
        WHEN 'AI ETF' THEN 'It is a thematic ETF that invests in artificial intelligence-related industries or companies.'
        WHEN '반도체ETF' THEN 'This is an ETF that tracks companies or indices related to the semiconductor industry.'
        WHEN '2차전지ETF' THEN 'This is an ETF that tracks companies or indices related to the secondary battery industry.'
        WHEN 'TR형ETF' THEN 'It is a type of ETF that tracks the total return index reinvested without paying distributions.'
        WHEN 'PR형ETF' THEN 'It is a type of ETF that tracks a price index excluding cash flows such as dividends.'
        WHEN '합성HETF' THEN 'This is an ETF expression that combines synthetic replication and foreign exchange hedging structures.'
        WHEN 'LP평가' THEN 'This is a procedure to evaluate the level of fulfillment of the liquidity provider’s obligation to present quotations.'
        WHEN '괴리율관리' THEN 'This is an activity to manage the ETF ETN market price so that it does not deviate significantly from the net asset value or index value.'
        WHEN '유동성공급호가' THEN 'This is a bid/ask price submitted by LP to provide market liquidity.'
        WHEN 'DATA SNAPSHOT' THEN 'The data at a specific reference point is fixed and stored.'
        WHEN 'FEATURE STORE' THEN 'This is a repository that stores and manages model input variables so that they can be reused.'
        WHEN 'MODEL REGISTRY' THEN 'This is a repository that manages model version and deployment status performance information.'
        WHEN 'EXPERIMENT RUN' THEN 'It is a recording unit that executes model learning or verification once.'
        WHEN 'TRAINING SET' THEN 'This is a data set used for model training.'
        WHEN 'VALIDATION SET' THEN 'This is a validation data set used for model selection and tuning.'
        WHEN 'TEST SET' THEN 'This is a separate data set used for final performance verification.'
        WHEN 'LABEL LEAKAGE' THEN 'This is a problem where performance is overestimated because the correct answer or future information is mixed with the input variables.'
        WHEN 'TARGET VARIABLE' THEN 'This is the target variable that the model is trying to predict.'
        WHEN 'PREDICTION HORIZON' THEN 'This is the period that indicates how far into the future the model predicts from the current point.'
        WHEN 'ROLLING RETRAIN' THEN 'This method retrains the model at a certain interval whenever new data is accumulated.'
        WHEN 'MODEL VERSION' THEN 'This is a version in which learning data and parameters are differentiated within the same model series.'
        WHEN 'FEATURE VERSION' THEN 'It is a version of the input variable calculation or creation method.'
        WHEN 'SCHEMA VERSION' THEN 'This is a version of the data column structure and type definition.'
        WHEN 'DATA CONTRACT' THEN 'It is a promise of column type quality standards defined between data providers and consumers.'
        WHEN 'QUALITY GATE' THEN 'These are quality standards that data or models must pass before moving on to the next step.'
        WHEN 'MONITORING ALERT' THEN 'This is a notification that occurs when operating data or model indicators deviate from the standards.'
        WHEN 'THRESHOLD RULE' THEN 'This is a rule that operates when a specific value exceeds or falls below a baseline.'
        WHEN 'SCORING BATCH' THEN 'It is a processing unit that calculates scores by combining multiple items or data.'
        WHEN 'RANKING BATCH' THEN 'It is a processing unit that collectively calculates searches or stock rankings.'
        WHEN 'REVIEW QUEUE' THEN 'This is a waiting list of terms or data items that need to be reviewed.'
        WHEN 'SOURCE CONFIDENCE' THEN 'This is a value that expresses the trust level of the source as a score or grade.'
        WHEN 'TERM STATUS' THEN 'This value indicates the status of the term, such as draft, review, complete, and public.'
        WHEN 'ALIAS STATUS' THEN 'This value indicates the status of the alias, such as candidate review completed, public, etc.'
        WHEN '옵션조정스프레드' THEN 'It is a credit spread calculated by removing the influence of options on bonds or structured products.'
        WHEN 'Z스프레드' THEN 'This is the spread needed to discount bond cash flows by adding a certain spread to the base interest rate curve.'
        WHEN '스프레드듀레이션' THEN 'It indicates the sensitivity of bond prices to changes in credit spreads.'
        WHEN '키레이트듀레이션' THEN 'It is the price sensitivity to changes in interest rates at a specific maturity in the yield curve.'
        WHEN '채권캐리' THEN 'This is the expected return from holding bonds as the difference between interest income and financing costs.'
        WHEN '롤다운수익' THEN 'It is the price change profit that occurs above the yield curve as time passes and the maturity period becomes shorter.'
        WHEN '총수익분해' THEN 'This is an analysis that divides investment performance into components such as price, interest, and exchange rate costs.'
        WHEN '금리베타' THEN 'It indicates the sensitivity of asset prices or yields to changes in interest rates.'
        WHEN '듀레이션중립' THEN 'The interest rate sensitivity of the portfolio has been neutralized according to specific standards.'
        WHEN '커브포지션' THEN 'It is a position exposed to changes in the slope or shape of the yield curve.'
        WHEN '스티프너전략' THEN 'This is a strategy that expects profits when the slope of the yield curve becomes steeper.'
        WHEN '플래트너전략' THEN 'This is a strategy that expects profits when the slope of the yield curve becomes gentle.'
        WHEN '신용등급전이' THEN 'This is a phenomenon in which an issuer''s credit rating moves from one rating to another.'
        WHEN '부도확률' THEN 'There is a possibility that the borrower or issuer will default on its obligations within the agreed period.'
        WHEN '회수율' THEN 'This is the percentage of principal and interest that a creditor can recover after a default.'
        WHEN '부도손실률' THEN 'This is the percentage of losses that are not expected to be recovered in case of bankruptcy.'
        WHEN '익스포저' THEN 'The amount of exposure or sensitivity to a specific risk factor or counterparty.'
        WHEN '예상손실' THEN 'This is the average expected loss that reflects the default probability loss rate exposure.'
        WHEN '비예상손실' THEN 'It is a loss of a volatile nature that may occur in excess of the expected loss.'
        WHEN '경제적자본' THEN 'This is capital that is deemed internally necessary to absorb unexpected losses.'
        WHEN '위험가중자산' THEN 'This is the size of assets used in calculating bank capital regulations, reflecting the risk of each asset.'
        WHEN '옵션델타헤지' THEN 'It is a hedge that adjusts delta to reduce the risk of changes in the price of the underlying asset.'
        WHEN '감마스캘핑' THEN 'This is a strategy that aims to profit by trading underlying assets using option gamma exposure.'
        WHEN '베가노출' THEN 'It is a risk exposure where option value changes depending on volatility changes.'
        WHEN '세타손실' THEN 'This is a loss that arises from the effect of the option value decreasing over time.'
        WHEN '내재상관' THEN 'This is the level of correlation between constituent stocks reflected in option prices or index products.'
        WHEN '상관거래' THEN 'It is a transaction that aims to profit from changes in the correlation between two assets or indices.'
        WHEN '페어트레이딩' THEN 'It is a strategy that takes advantage of the trend of widening or narrowing the price difference between two similar assets.'
        WHEN '스프레드수익률' THEN 'It is a performance calculated using the difference in return between two assets or indicators.'
        WHEN '가중평균만기' THEN 'It is the average value of the maturity of several bonds or cash flows in proportion to the amount.'
        WHEN '가중평균듀레이션' THEN 'It is the average value of the duration of the bonds in the portfolio by proportion.'
        WHEN '베어스티프닝' THEN 'This is a phenomenon in which long-term interest rates rise more than short-term interest rates, causing the yield curve to steepen.'
        WHEN '불플래트닝' THEN 'This is a phenomenon in which long-term interest rates fall more than short-term interest rates, resulting in a flattening of the yield curve.'
        WHEN '커브리스크' THEN 'It is a price risk that arises from changes in the slope or shape of the yield curve.'
        WHEN '리스크프리미엄분해' THEN 'It is an analysis that divides asset return into factors such as interest rate, credit, liquidity, risk compensation, etc.'
        WHEN '가계대출' THEN 'These are loans including home mortgage loans and credit loans that households borrow from financial institutions.'
        WHEN '주택담보대출' THEN 'This is a loan obtained by providing a house as collateral.'
        WHEN '전세자금대출' THEN 'This is a loan obtained from a financial institution to secure a deposit for a lease.'
        WHEN '기업대출' THEN 'This is a loan that a company receives from a financial institution to raise operating funds or investment funds.'
        WHEN '중소기업대출' THEN 'This is a financial institution loan provided to small and medium-sized businesses.'
        WHEN '예대율' THEN 'We look at fund management and liquidity level based on the ratio of loans to bank deposits.'
        WHEN '예금금리' THEN 'This is the interest rate paid by financial institutions on deposits.'
        WHEN '대출금리' THEN 'This is the interest rate paid by a borrower who has received a loan to a financial institution.'
        WHEN '금리상한' THEN 'This is the maximum interest rate that can be applied to loans or financial products.'
        WHEN '금리하한' THEN 'This is the minimum interest rate that can be applied to loans or financial products.'
        WHEN '콜시장' THEN 'It is a market that borrows and lends ultra-short-term funds between financial institutions.'
        WHEN 'CD시장' THEN 'It is a money market where certificates of deposit are issued and traded.'
        WHEN 'CP시장' THEN 'It is a money market where commercial papers are issued and traded.'
        WHEN '전자금융공동망' THEN 'It is a joint computer network that processes electronic fund transfers and inquiries between financial institutions.'
        WHEN '오픈뱅킹' THEN 'It is a service system that connects account inquiries and transfers from various financial institutions through standard APIs.'
        WHEN '마이데이터' THEN 'It is a data service system that allows individuals to comprehensively view and utilize their financial data.'
        WHEN '금융안정지수' THEN 'It is an index that combines various indicators of the level of instability in the financial system.'
        WHEN '조기경보지표' THEN 'It is a leading indicator used to detect the possibility of a crisis in advance.'
        WHEN '대외지급능력' THEN 'It is the ability of a country to cover foreign currency debt and external payment demands.'
        WHEN '총저축률' THEN 'It is the proportion of savings in total national disposable income.'
        WHEN '투자율' THEN 'It is the ratio of investment expenditure to gross domestic product or income.'
        WHEN '설비투자지수' THEN 'It is an economic indicator that indexes the flow of corporate facility investment.'
        WHEN '건설기성' THEN 'This is the amount of actual construction work performed by the construction company.'
        WHEN '소매판매액지수' THEN 'It is an indicator that shows the flow of consumption by indexing changes in retail sales.'
        WHEN '광공업생산' THEN 'It is an indicator of production activities in mining, manufacturing, electricity and gas industries.'
        WHEN '서비스업생산' THEN 'It is an indicator of changes in production activities in the service sector.'
        WHEN '제조업가동률' THEN 'It indicates how much manufacturing production facilities are operating compared to normal capacity.'
        WHEN '재고순환지표' THEN 'It is an indicator that judges the economic situation by looking at both shipments and inventory flows.'
        WHEN '수입의존도' THEN 'It is the share of imports in domestic demand or production.'
        WHEN '교역의존도' THEN 'Dependence on external transactions is measured by the ratio of imports and exports to gross domestic product.'
        WHEN '원화절상' THEN 'This is a phenomenon in which the value of the won increases compared to foreign currencies.'
        WHEN '원화절하' THEN 'This is a phenomenon in which the value of the won decreases compared to foreign currencies.'
        WHEN '환율패스스루' THEN 'This is the extent to which exchange rate changes are transmitted to import prices and consumer prices.'
        WHEN '통화가치' THEN 'It is the purchasing power of one currency over goods, services or other currencies.'
        WHEN '외화유동성' THEN 'It is the foreign currency funding capacity that can respond to the demand for foreign currency payments.'
        WHEN '공개매수기간' THEN 'This is the period during which a public buyer proposes to publicly purchase stocks.'
        WHEN '공개매수가격' THEN 'In a tender offer, this is the price offered by the buyer to purchase the stock.'
        WHEN '공개매수자' THEN 'An entity that seeks to acquire stocks through a public offer.'
        WHEN '대항공개매수' THEN 'It is a tender offer made competitively by another entity in opposition to an existing tender offer.'
        WHEN '합병비율' THEN 'This is the rate at which each company''s stock is exchanged for the surviving company''s stock in the event of a merger.'
        WHEN '분할비율' THEN 'This is the standard ratio by which assets, liabilities, and capital are divided when a company is split.'
        WHEN '주식매수청구권' THEN 'Shareholders who oppose major decisions such as mergers have the right to request the purchase of shares from the company.'
        WHEN '매수청구가격' THEN 'This is the standard price at which the company purchases stocks when the stock purchase right is exercised.'
        WHEN '신주상장예정일' THEN 'This is the date when new stocks are scheduled to be sold on the exchange.'
        WHEN '권리락기준가' THEN 'This is the standard price that is theoretically adjusted and applied after ex-rights occur.'
        WHEN '감자기준일' THEN 'This is the base date for determining the number of shareholders and shares on which the effect of capital reduction will be reflected.'
        WHEN '감자비율' THEN 'It is the ratio of the number of shares or capital that is reduced due to depletion.'
        WHEN '증자비율' THEN 'It is the ratio of the number of newly issued shares through capital increase to the number of existing shares.'
        WHEN '전환사채권면총액' THEN 'This is the total face amount of convertible bonds issued.'
        WHEN '전환청구기간' THEN 'This is the period during which convertible bond holders can request conversion to stock.'
        WHEN '전환가액조정' THEN 'This is a procedure to adjust the conversion price according to conditions such as stock price fluctuations or capital increase.'
        WHEN '리픽싱' THEN 'This is an adjustment that re-determines the conversion price or exercise price according to pre-determined conditions.'
        WHEN '행사가액조정' THEN 'This is a procedure to adjust the exercise price of rights, such as preemptive rights, according to conditions.'
        WHEN '신주인수권행사기간' THEN 'This is the period during which new stock warrantees can request to acquire new stock.'
        WHEN '교환대상주식' THEN 'These are stocks that can be exchanged for private bonds in exchangeable bonds, etc.'
        WHEN '사채만기일' THEN 'This is the final maturity date when the bond principal is scheduled to be repaid.'
        WHEN '표면이자율' THEN 'This is the interest rate agreed upon on the face amount of the bond.'
        WHEN '만기이자율' THEN 'This is the interest rate applied when bonds or debentures are held until maturity.'
        WHEN '조기상환청구권' THEN 'This is the right for investors to demand repayment before maturity.'
        WHEN '매도청구권' THEN 'It is the right for an issuer or a specific entity to purchase securities held by an investor.'
        WHEN '풋옵션조항' THEN 'It is a contract clause that allows investors to request a sale under specified conditions.'
        WHEN '콜옵션조항' THEN 'It is a contract provision that allows the issuer, etc. to request purchase under specified conditions.'
        WHEN '재무약정' THEN 'It is an agreement to maintain a certain financial ratio in a borrowing or investment contract.'
        WHEN '담보권설정' THEN 'It is the act of placing a lien on an asset to secure a claim.'
        WHEN '우발채무' THEN 'It is a potential liability that may become an actual liability depending on whether future events occur.'
        WHEN '특수관계자거래' THEN 'It is a transaction between a company and an individual or corporation with a special relationship.'
        WHEN '내부거래' THEN 'It is a transaction that occurs between companies within a business group or related parties.'
        WHEN '최대주주등소유주식변동' THEN 'It is true that the number of shares held by the largest shareholder and related parties has changed.'
        WHEN '임원보수' THEN 'This is the amount of remuneration paid to executives, including directors and auditors.'
        WHEN '감사보수' THEN 'This is compensation for audit services paid to external auditors.'
        WHEN '대량매매신청' THEN 'This is the process of applying to an exchange or securities company system for bulk trading.'
        WHEN '장중경쟁대량매매' THEN 'It is a system for trading large quantities of stocks in a competitive manner during regular trading.'
        WHEN '협의대량매매' THEN 'This is a method where the buying and selling parties negotiate the price and quantity and transact in bulk.'
        WHEN '시초가단일가' THEN 'It is a single-price transaction in which orders are collected before the market starts and the opening price is determined.'
        WHEN '장마감단일가' THEN 'It is a single price transaction in which orders are collected before the market closes and the closing price is determined.'
        WHEN '장중단일가' THEN 'This is a method of collecting orders for a certain period of time during the regular market and executing them at one price.'
        WHEN '거래량가중평균가' THEN 'It is the average price of the transaction price weighted by the transaction volume.'
        WHEN '체결우선순위' THEN 'This is an exchange principle that determines the order in which orders are executed.'
        WHEN '가격우선원칙' THEN 'The principle is that orders with more favorable prices are executed first.'
        WHEN '시간우선원칙' THEN 'The principle is that at the same price, the order received first is filled first.'
        WHEN '위탁매매' THEN 'This is a method in which securities firms receive orders from investors and trade them in the market.'
        WHEN '자기매매' THEN 'It is a transaction in which a financial investment company buys and sells securities on its own account.'
        WHEN '시장조성호가' THEN 'This is a bid/ask price submitted by a market maker to provide liquidity.'
        WHEN '유동성공급계약' THEN 'This is a contract between the issuer and LP to secure the liquidity of listed products.'
        WHEN '호가제한' THEN 'It is a device that limits the order price range for market stability or institutional reasons.'
        WHEN '착오거래' THEN 'This is a transaction that was concluded differently than intended due to order entry or system errors.'
        WHEN '착오거래구제' THEN 'This is a procedure to provide relief for serious erroneous transactions in accordance with exchange regulations.'
        WHEN '서킷브레이커1단계' THEN 'This is a trading halt that is triggered as the first step when the market plunges.'
        WHEN '서킷브레이커2단계' THEN 'It is a second-stage trading suspension that is triggered when the market plunges further.'
        WHEN '서킷브레이커3단계' THEN 'This is the final stage of action that is triggered when the market plunge becomes extreme.'
        WHEN '매매거래중단' THEN 'This is a measure to temporarily suspend trading in the entire market or specific products.'
        WHEN '주문폭주' THEN 'There is an excessive number of orders in a short period of time, creating a burden on the system and execution processing.'
        WHEN '시스템매매' THEN 'It is a trading method that creates and executes orders according to pre-determined rules and systems.'
        WHEN '알고리즘매매' THEN 'This is a trading where an algorithm automatically executes orders based on market data and conditions.'
        WHEN '고빈도매매' THEN 'It is an algorithmic trading that repeats many orders and cancellations in a very short period of time.'
        WHEN 'DMA' THEN 'This is a connection method in which investors send orders directly to a path close to the exchange system.'
        WHEN 'SOR' THEN 'This is order routing that automatically selects an advantageous execution path among multiple markets or quotes.'
        WHEN '최선집행' THEN 'This is the principle of executing customer orders under the best conditions considering price, cost, speed, possibility, etc.'
        WHEN '시장간스프레드' THEN 'It is the difference between the prices of the same asset listed on different markets.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '저축성보험',
        '변액보험',
        '변액연금보험',
        '저축은행예금',
        '파킹통장',
        '정기예금',
        '정기적금',
        '발행어음형CMA',
        '외화예금',
        '달러예금',
        '외화채권',
        '미국국채',
        '단기사채',
        '전자단기사채',
        'ABS',
        'MBS',
        'CLO',
        '커버드본드',
        '신종자본증권',
        '조건부자본증권',
        '코코본드',
        'ESG펀드',
        '인컴펀드',
        '배당주펀드',
        '커버드콜펀드',
        '타깃인컴펀드',
        '초단기채펀드',
        '만기매칭형펀드',
        '만기채권형ETF',
        '월배당ETF',
        '주식채권혼합ETF',
        '멀티에셋ETF',
        '액티브채권ETF',
        'AI ETF',
        '반도체ETF',
        '2차전지ETF',
        'TR형ETF',
        'PR형ETF',
        '합성HETF',
        'LP평가',
        '괴리율관리',
        '유동성공급호가',
        'DATA SNAPSHOT',
        'FEATURE STORE',
        'MODEL REGISTRY',
        'EXPERIMENT RUN',
        'TRAINING SET',
        'VALIDATION SET',
        'TEST SET',
        'LABEL LEAKAGE',
        'TARGET VARIABLE',
        'PREDICTION HORIZON',
        'ROLLING RETRAIN',
        'MODEL VERSION',
        'FEATURE VERSION',
        'SCHEMA VERSION',
        'DATA CONTRACT',
        'QUALITY GATE',
        'MONITORING ALERT',
        'THRESHOLD RULE',
        'SCORING BATCH',
        'RANKING BATCH',
        'REVIEW QUEUE',
        'SOURCE CONFIDENCE',
        'TERM STATUS',
        'ALIAS STATUS',
        '옵션조정스프레드',
        'Z스프레드',
        '스프레드듀레이션',
        '키레이트듀레이션',
        '채권캐리',
        '롤다운수익',
        '총수익분해',
        '금리베타',
        '듀레이션중립',
        '커브포지션',
        '스티프너전략',
        '플래트너전략',
        '신용등급전이',
        '부도확률',
        '회수율',
        '부도손실률',
        '익스포저',
        '예상손실',
        '비예상손실',
        '경제적자본',
        '위험가중자산',
        '옵션델타헤지',
        '감마스캘핑',
        '베가노출',
        '세타손실',
        '내재상관',
        '상관거래',
        '페어트레이딩',
        '스프레드수익률',
        '가중평균만기',
        '가중평균듀레이션',
        '베어스티프닝',
        '불플래트닝',
        '커브리스크',
        '리스크프리미엄분해',
        '가계대출',
        '주택담보대출',
        '전세자금대출',
        '기업대출',
        '중소기업대출',
        '예대율',
        '예금금리',
        '대출금리',
        '금리상한',
        '금리하한',
        '콜시장',
        'CD시장',
        'CP시장',
        '전자금융공동망',
        '오픈뱅킹',
        '마이데이터',
        '금융안정지수',
        '조기경보지표',
        '대외지급능력',
        '총저축률',
        '투자율',
        '설비투자지수',
        '건설기성',
        '소매판매액지수',
        '광공업생산',
        '서비스업생산',
        '제조업가동률',
        '재고순환지표',
        '수입의존도',
        '교역의존도',
        '원화절상',
        '원화절하',
        '환율패스스루',
        '통화가치',
        '외화유동성',
        '공개매수기간',
        '공개매수가격',
        '공개매수자',
        '대항공개매수',
        '합병비율',
        '분할비율',
        '주식매수청구권',
        '매수청구가격',
        '신주상장예정일',
        '권리락기준가',
        '감자기준일',
        '감자비율',
        '증자비율',
        '전환사채권면총액',
        '전환청구기간',
        '전환가액조정',
        '리픽싱',
        '행사가액조정',
        '신주인수권행사기간',
        '교환대상주식',
        '사채만기일',
        '표면이자율',
        '만기이자율',
        '조기상환청구권',
        '매도청구권',
        '풋옵션조항',
        '콜옵션조항',
        '재무약정',
        '담보권설정',
        '우발채무',
        '특수관계자거래',
        '내부거래',
        '최대주주등소유주식변동',
        '임원보수',
        '감사보수',
        '대량매매신청',
        '장중경쟁대량매매',
        '협의대량매매',
        '시초가단일가',
        '장마감단일가',
        '장중단일가',
        '거래량가중평균가',
        '체결우선순위',
        '가격우선원칙',
        '시간우선원칙',
        '위탁매매',
        '자기매매',
        '시장조성호가',
        '유동성공급계약',
        '호가제한',
        '착오거래',
        '착오거래구제',
        '서킷브레이커1단계',
        '서킷브레이커2단계',
        '서킷브레이커3단계',
        '매매거래중단',
        '주문폭주',
        '시스템매매',
        '알고리즘매매',
        '고빈도매매',
        'DMA',
        'SOR',
        '최선집행',
        '시장간스프레드'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '시장간차익거래' THEN 'It is a transaction that pursues risk-free or low-risk profits by using price differences between markets.'
        WHEN 'ETF차익거래' THEN 'This is a transaction that combines redemption and sales using the difference between the ETF market price and net asset value.'
        WHEN 'AP' THEN 'It is a designated participating company in charge of ETF establishment and redemption.'
        WHEN '설정환매' THEN 'This is the process of creating or canceling collective investment securities such as ETFs and exchanging them for assets.'
        WHEN '현물납입' THEN 'This is a method of paying a basket of underlying assets instead of cash when setting up an ETF.'
        WHEN 'ELT' THEN 'This is a product that provides a profit structure linked to stock indices, etc. in the form of a trust.'
        WHEN '특정금전신탁' THEN 'It is a money trust in which an investor entrusts a specific management method.'
        WHEN '불특정금전신탁' THEN 'It is a money trust in which the trustee manages various customer funds by determining the management method.'
        WHEN 'MMT' THEN 'This is a type of specific money trust that operates in short-term financial products.'
        WHEN '채권랩' THEN 'It is a wrap service that manages customer accounts focusing on bonds.'
        WHEN '브라질국채' THEN 'These are government bonds issued by the Brazilian government that carry both local currency and interest rate risks.'
        WHEN '장외채권' THEN 'These are bonds traded outside the exchange market through relative trading with securities companies.'
        WHEN '장내채권' THEN 'These are bonds traded in a standardized manner in the exchange bond market.'
        WHEN '소액채권' THEN 'These are bonds that are traded in small amounts or sold to general investors.'
        WHEN '국민주택채권' THEN 'This is a government bond issued to raise funds for the Housing and Urban Fund.'
        WHEN '지역개발채권' THEN 'These are bonds issued by local governments to raise funds for regional development.'
        WHEN '소매채권' THEN 'These are bonds sold to retail customers such as individual investors.'
        WHEN '회사채공모' THEN 'This is a method of soliciting and issuing corporate bonds to an unspecified number of investors.'
        WHEN '회사채사모' THEN 'This is a method of privately issuing corporate bonds to minority investors.'
        WHEN '전단채' THEN 'This is a practical expression for shortening electronic short-term bonds.'
        WHEN 'CP상품' THEN 'It is a short-term financial product sold based on commercial paper.'
        WHEN '금현물' THEN 'It is a product traded based on physical gold through an exchange or financial company.'
        WHEN 'KRX금시장' THEN 'It is a gold spot trading market operated by the Korea Exchange.'
        WHEN '금ETF' THEN 'This is an ETF that tracks gold prices or gold-related indices.'
        WHEN '원유ETF' THEN 'This is an ETF that tracks crude oil prices or crude oil futures indices.'
        WHEN '리츠ETF' THEN 'This is an ETF that tracks listed REITs or real estate-related indices.'
        WHEN '금리형ETF' THEN 'This is a type of ETF that pursues short-term interest rates or interest income.'
        WHEN '파킹형ETF' THEN 'This is a type of ETF that aims for short-term management of cash funds.'
        WHEN '머니마켓ETF' THEN 'This is an ETF that pursues money market returns.'
        WHEN '초단기ETF' THEN 'This is an ETF that invests in short-maturity bonds or short-term financial products.'
        WHEN '인버스2XETF' THEN 'This is an ETF that pursues performance that is twice the negative of the daily return of the underlying index.'
        WHEN '레버리지2XETF' THEN 'This is an ETF that seeks twice the daily return of the underlying index.'
        WHEN '액티브주식ETF' THEN 'It is an active ETF that forms a stock portfolio based on the operator’s judgment.'
        WHEN 'ETF랩' THEN 'It is a wrap service that constructs and operates a portfolio centered on ETFs.'
        WHEN '로보일임' THEN 'It is a service that operates independently using a robo-advisor algorithm.'
        WHEN '일임계약' THEN 'It is a contract in which an investor delegates investment judgment and management to a financial company.'
        WHEN '투자자문계약' THEN 'This is a contract where a financial company provides advice on investment decisions.'
        WHEN '온라인펀드' THEN 'It is a fund that you subscribe to or trade through online channels.'
        WHEN '클래스A' THEN 'This is a fund class that charges a pre-sale fee.'
        WHEN '클래스C' THEN 'This is a fund class with relatively high sales fees without upfront fees.'
        WHEN '클래스S' THEN 'This is a fund class mainly provided through online-only sales channels.'
        WHEN '클래스Ae' THEN 'This refers to an online advance fee fund class.'
        WHEN '클래스Ce' THEN 'This refers to an online follow-up or conservative fund class.'
        WHEN 'ARPU' THEN 'This indicator represents the average sales per user.'
        WHEN 'ARPPU' THEN 'This indicator represents the average sales per paying user.'
        WHEN 'CAC' THEN 'This is the average cost to acquire one customer.'
        WHEN '고객생애가치' THEN 'It is the total profit that a customer is expected to generate over the life of the relationship.'
        WHEN '고객획득비용' THEN 'Marketing and sales costs incurred to acquire new customers.'
        WHEN '해지율' THEN 'This is the rate at which customers or contracts churn over a certain period of time.'
        WHEN '순매출유지율' THEN 'It is a retention ratio that reflects the expansion, contraction, and termination of existing customer sales.'
        WHEN '총매출유지율' THEN 'This is the percentage of sales from existing customers that is maintained excluding expansion effects.'
        WHEN '반복매출' THEN 'It is a revenue that occurs repeatedly at a certain cycle, such as a subscription fee.'
        WHEN '월반복매출' THEN 'This is subscription-type revenue that occurs repeatedly every month.'
        WHEN '연반복매출' THEN 'This is recurring sales converted to annual basis.'
        WHEN '구독매출' THEN 'This is revenue generated from regular subscription contracts.'
        WHEN '계약잔고' THEN 'This is the remaining amount from the concluded contract that has not yet been recognized as sales.'
        WHEN '수주잔고' THEN 'This is the quantity or amount for which an order has been received but delivery or sales recognition has not yet been completed.'
        WHEN '신규수주' THEN 'This is the amount of a new order or contract secured over a certain period of time.'
        WHEN '수주총액' THEN 'It is the total amount of orders secured during a specific period or on a cumulative basis.'
        WHEN '매출인식' THEN 'This is the process of recording sales in accounting according to the extent to which goods or services are provided.'
        WHEN '진행률기준' THEN 'This is the standard for recognizing revenue depending on the progress of construction or services.'
        WHEN '완성기준' THEN 'This is the standard for recognizing revenue when the provision of goods or services is completed.'
        WHEN '매출채권연령분석' THEN 'This is an analysis that evaluates the risk of collection by dividing trade receivables by elapsed period.'
        WHEN '부실채권' THEN 'It is a bond that is difficult to collect and has a high possibility of loss.'
        WHEN '제각' THEN 'This is an accounting treatment that removes from the books receivables or assets that are judged to be impossible to collect.'
        WHEN '채권회수율' THEN 'It is the ratio of the amount actually recovered from non-performing or mature receivables.'
        WHEN '재고평가충당금' THEN 'This is an adequacy evaluation account recognized in preparation for a decline in inventory value.'
        WHEN '제품보증충당부채' THEN 'This is a provision recognized in preparation for warranty obligations for sold products.'
        WHEN '판매보증충당부채' THEN 'This is a provision established to prepare for post-sale warranty service costs.'
        WHEN '감모손실' THEN 'This is a loss that occurs when the quantity of inventory or assets decreases due to natural decline or management loss.'
        WHEN '폐기손실' THEN 'This is a loss incurred by disposing of inventory assets that are difficult to use or sell.'
        WHEN '원재료' THEN 'It is a basic material that is directly used in the production of products.'
        WHEN '재공품' THEN 'This product is in the production process and has not yet been completed.'
        WHEN '제품' THEN 'It is a finished product that has completed production and is ready for sale.'
        WHEN '상품' THEN 'It is a good that is purchased externally and sold without additional manufacturing.'
        WHEN '미착품' THEN 'These are inventory assets that have been purchased but have not yet arrived at the company.'
        WHEN 'TERM REVIEW' THEN 'This is a work unit that reviews dictionary definitions and metadata.'
        WHEN 'SOURCE REVIEW' THEN 'This is a review procedure to check the suitability and reliability of terminology sources.'
        WHEN 'ALIAS REVIEW' THEN 'This is a procedure to check whether the alias is correctly connected to the canonical term.'
        WHEN 'DUPLICATE CHECK' THEN 'This is a verification procedure that finds duplicate terms or aliases based on normalization.'
        WHEN 'CANONICAL TERM' THEN 'It is an official term managed as a representative entry in a dictionary.'
        WHEN 'NORMALIZED TERM' THEN 'This is a term value with spaces and upper and lower case letters organized for search and duplicate checking.'
        WHEN 'INITIAL INDEX' THEN 'This is an index value that groups dictionary entries by initial consonants or the first letter of the alphabet.'
        WHEN 'IMPORT RUN' THEN 'This is an execution unit that retrieves dictionary items from CSV or external data.'
        WHEN 'IMPORT PROFILE' THEN 'Spring profile or execution settings to be used in the import task.'
        WHEN 'CSV SEED' THEN 'This is a CSV file containing dictionary initial data or extended data.'
        WHEN 'SOURCE URL' THEN 'This is URL metadata that allows you to check the source of a term.'
        WHEN 'SOURCE ORG' THEN 'This is metadata that indicates the institution or organization from which the term originated.'
        WHEN 'REVIEWED AT' THEN 'This is metadata that records when a term was reviewed.'
        WHEN 'PUBLISHED TERM' THEN 'This is a term that can be disclosed in service search results.'
        WHEN 'DRAFT TERM' THEN 'It is a term that is still under review and is in the pre-publication stage.'
        WHEN 'DEPRECATED TERM' THEN 'It is a term that is no longer used as a representative term and needs to be replaced.'
        WHEN 'TERM MERGE' THEN 'This is the process of combining duplicate or similar terms into one canonical term.'
        WHEN 'ALIAS MERGE' THEN 'This is a task of organizing duplicate aliases and consolidating them into one canonical term connection.'
        WHEN 'SEARCH TOKEN' THEN 'It is a token unit that divides terms or queries for search processing.'
        WHEN 'QUERY NORMALIZATION' THEN 'This is a process that organizes user search terms into a comparable form.'
        WHEN 'KOREAN INITIAL' THEN 'This is an index value made from the initial consonant of the first letter of a Korean term.'
        WHEN 'AUTOCOMPLETE CANDIDATE' THEN 'This is a term or alias candidate value that can be exposed to autocompletion.'
        WHEN 'SEARCH BOOST' THEN 'This is the weight given to specific conditions to increase the ranking of search results.'
        WHEN 'RANKING FEATURE' THEN 'This is an input feature used for searches or stock ranking calculations.'
        WHEN 'EXACT BOOST' THEN 'Additional points are given to items that exactly match the search term.'
        WHEN 'ALIAS BOOST' THEN 'This is the weight given to adjust the ranking of alias matching results.'
        WHEN 'SOURCE TIER' THEN 'It is a value that divides source reliability and usage into levels.'
        WHEN 'REVIEW STATUS' THEN 'This value indicates the status of a term or data during the review process.'
        WHEN 'DATA QUALITY ISSUE' THEN 'This is an item in which quality problems such as data omission, duplication, and inconsistency were discovered.'
        WHEN 'COVERAGE GAP' THEN 'This is a gap in which dictionaries or datasets do not sufficiently cover the required range.'
        WHEN 'TERM BACKLOG' THEN 'This is a candidate list of terms that require further collection or review.'
        WHEN 'COLLECTION BACKLOG' THEN 'This is a task list that organizes the sources and terminology areas to be collected in the future.'
        WHEN 'OFFICIAL SOURCE' THEN 'It is a source that can be used as primary evidence, such as official agency or institutional documents.'
        WHEN 'BROKER SOURCE' THEN 'This is a secondary source obtained from financial company data such as securities firms and bank management companies.'
        WHEN 'INTERNAL SOURCE' THEN 'This is an internal source defined by QAIMA service and analysis logic.'
        WHEN '대량보유목적' THEN 'In the bulk holding report, this is an item that divides the purpose of stock holding into simple investment, general investment, influence on management rights, etc.'
        WHEN '단순투자목적' THEN 'It is a purpose category of holding stocks for the purpose of investment profit without the intention to participate in management.'
        WHEN '일반투자목적' THEN 'The holding purpose includes limited shareholder activities such as dividends or suggestions for improvement of governance structure.'
        WHEN '경영권영향목적' THEN 'The purpose of the holding is to influence the management of the company, such as appointing executives or changing the articles of incorporation.'
        WHEN '공동보유자' THEN 'They are investors who have decided to jointly own stocks and exercise voting rights.'
        WHEN '특별관계자범위' THEN 'The scope of judgment includes special relationships and joint holding relationships in bulk holding reports, etc.'
        WHEN '보고의무발생일' THEN 'This is the base date when disclosure or reporting obligations arise.'
        WHEN '보유비율변동' THEN 'The ratio to the total number of issued stocks changes due to changes in the number of stocks held.'
        WHEN '소유상황보고' THEN 'This is a procedure by which executives or major shareholders report their ownership of specific securities.'
        WHEN '변동상황보고' THEN 'This is a procedure for reporting changes in the quantity of stocks or specific securities held.'
        WHEN '의결권대리행사' THEN 'This is the act of a shareholder delegating the exercise of voting rights to another person to exercise their voting rights at a general shareholders'' meeting.'
        WHEN '위임장권유' THEN 'It is an act of solicitation that asks shareholders to delegate voting rights.'
        WHEN '주주총회결과' THEN 'This is a disclosure that informs the resolution of each agenda item and major decisions at the general shareholders'' meeting.'
        WHEN '이사회결의공시' THEN 'This is a public announcement that informs the board of directors of major matters after they have been resolved.'
        WHEN '주요경영사항' THEN 'This is a matter related to company management that can have a significant impact on investment decisions.'
        WHEN '영업실적전망' THEN 'This is public information that the company presents performance forecasts such as future sales and profits.'
        WHEN '매출액손익구조변경' THEN 'This is a disclosure that is made when there is a significant change in sales or profit and loss structure.'
        WHEN '자본잠식' THEN 'Due to accumulated losses, the total capital is less than the capital.'
        WHEN '완전자본잠식' THEN 'The total capital has become less than 0, and all capital has been eroded.'
        WHEN '감사의견한정' THEN 'The auditor has presented a qualified opinion due to some scope limitations or differences of opinion.'
        WHEN '감사의견거절' THEN 'The auditor declined to express an opinion because he did not obtain sufficient audit evidence.'
        WHEN '감사의견부적정' THEN 'The auditor determined that the financial statements did not meet the standards and issued an inappropriate opinion.'
        WHEN '계속기업불확실성' THEN 'There is significant uncertainty as to whether the company will be able to continue operating.'
        WHEN '상장유지요건' THEN 'This is a requirement that a listed company must meet to maintain its listing on the exchange.'
        WHEN '형식상장폐지' THEN 'This is a delisting procedure that is carried out due to failure to meet formal requirements such as financial or public disclosure.'
        WHEN '실질심사사유' THEN 'This is a reason to actually review listing eligibility.'
        WHEN '기업심사위원회' THEN 'This is a committee that deliberates whether to maintain listing through substantive examination of listing eligibility.'
        WHEN '시장위원회' THEN 'This is a committee that deliberates on exchange market operations and delisting objections.'
        WHEN '개선계획이행내역' THEN 'These are the details of the improvement plan implemented by the company during the improvement period.'
        WHEN '감사인지정' THEN 'It is a system or procedure by which the supervisory authority appoints a company''s external auditor.'
        WHEN '주기적지정제' THEN 'It is a system that periodically appoints external auditors for companies that meet certain requirements.'
        WHEN '감사인직권지정' THEN 'This is a procedure in which the supervisory authority appoints a company''s auditor ex officio for specific reasons.'
        WHEN '감사계약체결' THEN 'This involves signing an audit service contract between a company and an external auditor.'
        WHEN '감사계약해지' THEN 'This means that the existing external audit contract is terminated prematurely.'
        WHEN '핵심감사사항기재' THEN 'This is the process of identifying, explaining, and recording key audit matters in the audit report.'
        WHEN '내부회계감사' THEN 'This is a procedure to audit the design and operational effectiveness of the internal accounting management system.'
        WHEN '위반행위공시' THEN 'This is a disclosure that informs the market of violations of disclosure obligations or capital market regulations.'
        WHEN '제재조치' THEN 'It is an administrative or market measure imposed for violations of laws or public notices.'
        WHEN '과징금부과' THEN 'This is a measure to impose monetary fines for violations of regulations.'
        WHEN '검찰고발' THEN 'This is a measure to report serious illegal charges to an investigative agency.'
        WHEN '불공정거래조사' THEN 'This is a procedure to investigate allegations of unfair transactions, such as market manipulation and use of undisclosed information.'
        WHEN '고유동성자산' THEN 'It is a high-quality asset that can be quickly converted into cash during a short-term liquidity crisis.'
        WHEN '시스템적중요은행' THEN 'This bank is subject to additional regulations due to its significant impact on the stability of the financial system.'
        WHEN '경기대응완충자본' THEN 'It is a macroprudential capital buffer that allows banks to build up additional capital during times of credit expansion.'
        WHEN '자본보전완충자본' THEN 'This is capital that a bank must hold in addition to its basic capital requirements to absorb losses.'
        WHEN '레버리지규제' THEN 'It is a non-risk-weighted regulation aimed at limiting excessive borrowing and asset expansion by banks.'
        WHEN '예금보험제도' THEN 'It is a system that protects depositors up to a certain limit in the event of a financial company''s bankruptcy.'
        WHEN '지급보증' THEN 'This is an act where a third party guarantees payment when the debtor does not fulfill his obligations.'
        WHEN '신용보강' THEN 'It is a device such as collateral guarantee provided to increase the credit rating of bonds or structured products.'
        WHEN '금융시장인프라' THEN 'It is a core system that supports financial transactions such as payment, clearing, and settlement.'
        WHEN '중앙예탁기관' THEN 'It is an institution that centrally deposits securities and supports rights transfer and settlement.'
        WHEN '증권결제시스템' THEN 'It is a system that settles the price and securities after a securities transaction.'
        WHEN '외환결제리스크' THEN 'In a foreign exchange transaction, it is the risk of not receiving the opposite currency after paying one currency.'
        WHEN 'CLS결제' THEN 'It is a payment method that reduces foreign exchange settlement risk through the foreign exchange simultaneous settlement system.'
        WHEN '국가부도위험' THEN 'There is a possibility that the country will not be able to repay foreign or national debt as agreed.'
        WHEN '쌍둥이적자' THEN 'The fiscal balance and current account balance are showing deficits at the same time.'
        WHEN '재정승수' THEN 'This is the size of the effect that government spending or tax changes have on gross domestic product.'
        WHEN '국채발행한도' THEN 'This is the legal or budgetary limit at which the government can issue government bonds.'
        WHEN '세입' THEN 'It is a resource that the government collects through taxes and other revenues.'
        WHEN '세출' THEN 'This is the amount the government spends for financial activities.'
        WHEN '기초재정수지' THEN 'It is the difference between government revenues and expenditures, excluding interest expenditures.'
        WHEN '총부채원리금상환액' THEN 'It is the total amount of principal and interest that the borrower must repay over a certain period of time.'
        WHEN '상환능력심사' THEN 'This is a process that evaluates the possibility of loan repayment based on the borrower''s income, debt, and credit.'
        WHEN '총부채상환능력' THEN 'It is the borrower''s ability to cover the principal and interest of all debt.'
        WHEN '스트레스DSR' THEN 'This is a conservatively calculated DSR that reflects the possibility of interest rate increases.'
        WHEN '가산금리' THEN 'It is an interest rate added to the base interest rate that reflects the borrower''s credit risk and transaction conditions.'
        WHEN '우대금리' THEN 'This is an interest rate that is lowered depending on transaction performance or satisfaction of conditions.'
        WHEN '기준금리연동' THEN 'It is a structure in which interest rates on loans or products fluctuate according to a specific base interest rate.'
        WHEN '혼합형금리' THEN 'It is a loan interest rate structure that changes to a floating interest rate after a fixed interest rate for a certain period of time.'
        WHEN '고정금리' THEN 'The structure is such that the interest rate of a loan or financial product does not change during the contract period.'
        WHEN '변동금리' THEN 'The applied interest rate changes depending on changes in the market interest rate or base interest rate.'
        WHEN '만기일시상환' THEN 'This is a repayment method in which only interest is paid until maturity and the principal is repaid all at once.'
        WHEN '원리금균등상환' THEN 'This is a method of repaying the loan so that the repayment amount including principal and interest is the same every period.'
        WHEN '원금균등상환' THEN 'This method involves repaying the same principal each period and paying interest on the remaining principal.'
        WHEN '거치기간' THEN 'This is the period in which only interest is paid on a loan without repayment of principal.'
        WHEN '주문북' THEN 'It is a ledger that shows the bid and ask prices submitted to the market and the remaining amount by price range.'
        WHEN '오더플로우' THEN 'It refers to the direction, size, speed, and flow of orders entering the market.'
        WHEN '호가불균형' THEN 'The balance of buy and sell prices is biased to one side.'
        WHEN '수급불균형' THEN 'Price pressure is created because buying and selling demand are not balanced.'
        WHEN '순매수' THEN 'It is a net sale where the purchase amount or quantity is greater than the sale.'
        WHEN '순매도' THEN 'It is a net sale situation where the selling amount or quantity is greater than the buying amount.'
        WHEN '개인순매수' THEN 'It is the net purchase size calculated by subtracting sales from purchases by individual investors.'
        WHEN '기관순매수' THEN 'It is the net purchase size minus sales from institutional investor purchases.'
        WHEN '외국인순매수' THEN 'It is the net purchase size calculated by subtracting sales from purchases by foreign investors.'
        WHEN '프로그램순매수' THEN 'It is the net purchase size obtained by subtracting the selling amount from the buying amount of program trading.'
        WHEN '차익순매수' THEN 'This is the net purchase size of arbitrage program trading.'
        WHEN '비차익순매수' THEN 'This is the net purchase size of non-arbitrage program trading.'
        WHEN '공매도비중' THEN 'This is the proportion of short selling out of the total trading value or trading volume.'
        WHEN '대차잔고비율' THEN 'It is the ratio of balance to the number of listed or outstanding stocks.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '시장간차익거래',
        'ETF차익거래',
        'AP',
        '설정환매',
        '현물납입',
        'ELT',
        '특정금전신탁',
        '불특정금전신탁',
        'MMT',
        '채권랩',
        '브라질국채',
        '장외채권',
        '장내채권',
        '소액채권',
        '국민주택채권',
        '지역개발채권',
        '소매채권',
        '회사채공모',
        '회사채사모',
        '전단채',
        'CP상품',
        '금현물',
        'KRX금시장',
        '금ETF',
        '원유ETF',
        '리츠ETF',
        '금리형ETF',
        '파킹형ETF',
        '머니마켓ETF',
        '초단기ETF',
        '인버스2XETF',
        '레버리지2XETF',
        '액티브주식ETF',
        'ETF랩',
        '로보일임',
        '일임계약',
        '투자자문계약',
        '온라인펀드',
        '클래스A',
        '클래스C',
        '클래스S',
        '클래스Ae',
        '클래스Ce',
        'ARPU',
        'ARPPU',
        'CAC',
        '고객생애가치',
        '고객획득비용',
        '해지율',
        '순매출유지율',
        '총매출유지율',
        '반복매출',
        '월반복매출',
        '연반복매출',
        '구독매출',
        '계약잔고',
        '수주잔고',
        '신규수주',
        '수주총액',
        '매출인식',
        '진행률기준',
        '완성기준',
        '매출채권연령분석',
        '부실채권',
        '제각',
        '채권회수율',
        '재고평가충당금',
        '제품보증충당부채',
        '판매보증충당부채',
        '감모손실',
        '폐기손실',
        '원재료',
        '재공품',
        '제품',
        '상품',
        '미착품',
        'TERM REVIEW',
        'SOURCE REVIEW',
        'ALIAS REVIEW',
        'DUPLICATE CHECK',
        'CANONICAL TERM',
        'NORMALIZED TERM',
        'INITIAL INDEX',
        'IMPORT RUN',
        'IMPORT PROFILE',
        'CSV SEED',
        'SOURCE URL',
        'SOURCE ORG',
        'REVIEWED AT',
        'PUBLISHED TERM',
        'DRAFT TERM',
        'DEPRECATED TERM',
        'TERM MERGE',
        'ALIAS MERGE',
        'SEARCH TOKEN',
        'QUERY NORMALIZATION',
        'KOREAN INITIAL',
        'AUTOCOMPLETE CANDIDATE',
        'SEARCH BOOST',
        'RANKING FEATURE',
        'EXACT BOOST',
        'ALIAS BOOST',
        'SOURCE TIER',
        'REVIEW STATUS',
        'DATA QUALITY ISSUE',
        'COVERAGE GAP',
        'TERM BACKLOG',
        'COLLECTION BACKLOG',
        'OFFICIAL SOURCE',
        'BROKER SOURCE',
        'INTERNAL SOURCE',
        '대량보유목적',
        '단순투자목적',
        '일반투자목적',
        '경영권영향목적',
        '공동보유자',
        '특별관계자범위',
        '보고의무발생일',
        '보유비율변동',
        '소유상황보고',
        '변동상황보고',
        '의결권대리행사',
        '위임장권유',
        '주주총회결과',
        '이사회결의공시',
        '주요경영사항',
        '영업실적전망',
        '매출액손익구조변경',
        '자본잠식',
        '완전자본잠식',
        '감사의견한정',
        '감사의견거절',
        '감사의견부적정',
        '계속기업불확실성',
        '상장유지요건',
        '형식상장폐지',
        '실질심사사유',
        '기업심사위원회',
        '시장위원회',
        '개선계획이행내역',
        '감사인지정',
        '주기적지정제',
        '감사인직권지정',
        '감사계약체결',
        '감사계약해지',
        '핵심감사사항기재',
        '내부회계감사',
        '위반행위공시',
        '제재조치',
        '과징금부과',
        '검찰고발',
        '불공정거래조사',
        '고유동성자산',
        '시스템적중요은행',
        '경기대응완충자본',
        '자본보전완충자본',
        '레버리지규제',
        '예금보험제도',
        '지급보증',
        '신용보강',
        '금융시장인프라',
        '중앙예탁기관',
        '증권결제시스템',
        '외환결제리스크',
        'CLS결제',
        '국가부도위험',
        '쌍둥이적자',
        '재정승수',
        '국채발행한도',
        '세입',
        '세출',
        '기초재정수지',
        '총부채원리금상환액',
        '상환능력심사',
        '총부채상환능력',
        '스트레스DSR',
        '가산금리',
        '우대금리',
        '기준금리연동',
        '혼합형금리',
        '고정금리',
        '변동금리',
        '만기일시상환',
        '원리금균등상환',
        '원금균등상환',
        '거치기간',
        '주문북',
        '오더플로우',
        '호가불균형',
        '수급불균형',
        '순매수',
        '순매도',
        '개인순매수',
        '기관순매수',
        '외국인순매수',
        '프로그램순매수',
        '차익순매수',
        '비차익순매수',
        '공매도비중',
        '대차잔고비율'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '대차체결량' THEN 'This is the quantity of stocks for which lending and lending transactions have been newly concluded.'
        WHEN '대차상환량' THEN 'This is the amount of borrowed stocks repaid.'
        WHEN '차입잔고' THEN 'It is the balance of securities or funds that have been borrowed but have not yet been repaid.'
        WHEN '매수압력' THEN 'This is pressure that induces price increases due to strong buy orders or strong supply and demand.'
        WHEN '매도압력' THEN 'This is pressure that causes prices to fall due to strong sell orders or strong supply and demand.'
        WHEN '호가흡수' THEN 'This is a phenomenon in which a large order is executed while digesting the quantity of the other party’s asking price.'
        WHEN '유동성공백' THEN 'Due to the lack of asking price and trading volume, the price can fluctuate significantly even with small orders.'
        WHEN '틱데이터' THEN 'This is data that records transactions or price changes in execution units or very short units.'
        WHEN '분봉' THEN 'This is bar data that aggregates price movements in short time units such as 1 minute and 5 minutes.'
        WHEN '일봉' THEN 'This is price data that compiles the opening, high, low, and closing prices on a daily basis.'
        WHEN '주봉' THEN 'This is bar data that aggregates price movements on a weekly basis.'
        WHEN '월봉' THEN 'This is bar data that aggregates price trends on a monthly basis.'
        WHEN '거래량급증' THEN 'Trading volume has increased significantly compared to usual.'
        WHEN '거래대금급증' THEN 'The transaction amount has increased significantly compared to usual.'
        WHEN '신고가' THEN 'This is the highest price recorded over a certain period of time.'
        WHEN '신저가' THEN 'This is the lowest price recorded over a certain period of time.'
        WHEN '갭상승' THEN 'This is an increase that creates a price gap because the market started at a higher price than the previous closing price.'
        WHEN '갭하락' THEN 'It is a decline that creates a price gap as the market starts at a lower price than the previous closing price.'
        WHEN '장대양봉' THEN 'The closing price is significantly higher than the opening price, indicating a strong rise.'
        WHEN '장대음봉' THEN 'This is a bar where the closing price is significantly lower than the opening price, indicating a strong decline.'
        WHEN '추세전환' THEN 'This is a phenomenon in which the direction of price or indicator changes in the opposite direction from the existing trend.'
        WHEN '상방돌파' THEN 'A movement in which the price crosses a resistance line or baseline.'
        WHEN '하방이탈' THEN 'It is a movement in which the price deviates below the support line or baseline.'
        WHEN '지지선' THEN 'This is a price range where the price decline is likely to stop or rebound.'
        WHEN '저항선' THEN 'This is a price range where price increases are likely to be prevented or adjusted.'
        WHEN '개인형IRP' THEN 'This is an individual retirement pension account that allows individuals to save and manage retirement benefits or additional contributions.'
        WHEN '기업형IRP' THEN 'This is a type of IRP that companies use to operate an employee retirement benefit system.'
        WHEN '퇴직IRP' THEN 'This refers to an IRP account that receives and operates retirement benefits.'
        WHEN '연금계좌' THEN 'Like pension savings and IRP, this is an account that receives tax benefits and manages retirement funds.'
        WHEN '연금저축계좌' THEN 'It is a tax-advantaged account that allows you to manage pension savings funds, insurance trusts, etc.'
        WHEN '연금저축신탁' THEN 'It is a pension savings product operated in the form of a trust.'
        WHEN '연금계좌이전' THEN 'This is a procedure to transfer pension savings or IRP to another financial company or product.'
        WHEN '연금납입한도' THEN 'This is the limit on the amount of contributions that can receive tax benefits into a pension account.'
        WHEN '연금수령한도' THEN 'This is the receipt limit that must be adhered to in order to receive a low tax rate from a pension account.'
        WHEN '연금개시연령' THEN 'This is the minimum or standard age at which you can start receiving pension.'
        WHEN '연금계좌해지' THEN 'This is the process of terminating the pension account contract and withdrawing accumulated funds.'
        WHEN 'ISA납입한도' THEN 'This is the annual or total limit on the amount that can be paid into an ISA account.'
        WHEN 'ISA비과세한도' THEN 'This is the limit on the amount of profits generated from an ISA that is exempt from taxation.'
        WHEN 'ISA의무가입기간' THEN 'This is the minimum subscription period that must be maintained to receive ISA tax benefits.'
        WHEN 'ISA중도해지' THEN 'This is the process of canceling an ISA before the mandatory subscription period.'
        WHEN '서민형ISA' THEN 'This is a type of ISA that provides greater tax benefits to subscribers who meet income requirements.'
        WHEN '일반형ISA' THEN 'This is the basic ISA type used by general subscribers.'
        WHEN '농어민형ISA' THEN 'This is a type of ISA for subscribers who meet the requirements for farmers and fishermen.'
        WHEN '발행어음약정' THEN 'This is an agreement that an investor enters into with a financial company to trade a bill of exchange.'
        WHEN '약정형RP' THEN 'This is a type of RP product that is purchased under set period and interest rate conditions.'
        WHEN '수시형RP' THEN 'It is a type of RP product that has a short maturity or is operated close to frequent deposits and withdrawals.'
        WHEN '자동매수형RP' THEN 'It is a service that automatically purchases and operates deposits, etc. with RP.'
        WHEN '외화MMF' THEN 'It is a money market fund that invests in foreign currency short-term financial products.'
        WHEN '달러MMF' THEN 'It is a money market fund that invests in U.S. dollar-denominated short-term financial products.'
        WHEN '국내채권' THEN 'These are bonds issued by domestic issuers in the Korean won or domestic market.'
        WHEN '해외채권' THEN 'These are bonds issued by overseas issuers or in overseas markets.'
        WHEN '달러표시채권' THEN 'This is a bond in which the principal and interest are paid in U.S. dollars.'
        WHEN '변동금리채권' THEN 'This is a bond whose coupon interest rate is adjusted according to changes in the base interest rate.'
        WHEN '물가연동채권' THEN 'It is a bond whose principal or interest is adjusted in relation to prices.'
        WHEN '신용연계채권' THEN 'These are bonds whose returns are linked to whether a credit event occurs for a specific company or index.'
        WHEN '구조화채권' THEN 'It is a bond designed so that cash flow varies depending on conditions such as interest rates, exchange rates, stock prices, and credit.'
        WHEN '은행채' THEN 'These are bonds issued by banks to raise funds.'
        WHEN '여전채' THEN 'These are bonds issued by specialized credit finance companies.'
        WHEN '카드채' THEN 'These are bonds issued by credit card companies to raise funds.'
        WHEN '캐피탈채' THEN 'These are bonds issued by capital companies to raise funds.'
        WHEN '금융채' THEN 'This refers to bonds issued by financial companies.'
        WHEN '후순위금융채' THEN 'These are bonds issued by financial companies that rank behind general bonds in terms of repayment.'
        WHEN '신탁형ISA보수' THEN 'This is a fee charged for operating and managing a trust-type ISA.'
        WHEN '일임형ISA보수' THEN 'This is a fee charged for discretionary ISA management services.'
        WHEN '투자일임수수료' THEN 'This is a fee borne by investors for providing discretionary investment services.'
        WHEN '투자자문수수료' THEN 'This is a fee paid by investors for providing investment advisory services.'
        WHEN '랩수수료' THEN 'This is a fee borne by investors for the operation and management of the wrap service.'
        WHEN '성과연동보수' THEN 'It is a compensation structure that varies depending on management performance.'
        WHEN '환매제한펀드' THEN 'It is a fund whose redemption is restricted for a certain period of time or only possible conditionally.'
        WHEN '개방형펀드' THEN 'This is a type of fund that investors can redeem according to established procedures.'
        WHEN '폐쇄형펀드' THEN 'This is a type of fund where redemption before maturity is restricted and must be recovered through market trading.'
        WHEN '상장폐쇄형펀드' THEN 'Although it has a closed structure, it is a fund that is listed and traded on the exchange.'
        WHEN '순매출' THEN 'This is actual sales after deducting discounts and returns from sales.'
        WHEN '총매출' THEN 'This is the total sales volume before reflecting deduction items.'
        WHEN '매출할인' THEN 'This is the discount amount deducted from sales according to sales conditions.'
        WHEN '매출반품' THEN 'This is the amount deducted from sales when sold products are returned.'
        WHEN '매출에누리' THEN 'This is the amount deducted from sales due to price adjustments or quality issues after sales.'
        WHEN '순매출총이익률' THEN 'It is the gross profit ratio calculated based on net sales.'
        WHEN '매출원가구성비' THEN 'It is the ratio of cost of sales divided by components such as raw materials, labor costs, and manufacturing expenses.'
        WHEN '원재료비' THEN 'This is the cost of using raw materials used to produce a product.'
        WHEN '노무비' THEN 'It is a cost in the nature of labor costs incurred in producing a product or providing a service.'
        WHEN '제조경비' THEN 'It is the indirect cost of the manufacturing process excluding raw material costs and labor costs.'
        WHEN '제조간접비' THEN 'It is a manufacturing cost that is common to many products and needs to be allocated.'
        WHEN '감가상각방법' THEN 'This is a method of allocating the depreciation cost of tangible assets by period.'
        WHEN '정액법' THEN 'This is a method of recognizing depreciation expense at the same amount each period.'
        WHEN '정률법' THEN 'This is a method of calculating depreciation by applying a certain ratio to the book value of an asset.'
        WHEN '생산량비례법' THEN 'This is a method of recognizing depreciation expenses in proportion to asset usage or production.'
        WHEN '매출채권담보차입' THEN 'This is a transaction in which trade receivables are provided as collateral and funds are borrowed.'
        WHEN '재고자산담보차입' THEN 'This is a transaction in which inventory assets are provided as collateral and funds are borrowed.'
        WHEN '운전자금대출' THEN 'This is a loan to raise funds necessary for a company''s daily business activities.'
        WHEN '시설자금대출' THEN 'This loan is necessary for long-term asset investment, such as facility investment or real estate acquisition.'
        WHEN '매출채권보험' THEN 'This is insurance that covers the risk of not being able to collect trade receivables due to customer bankruptcy, etc.'
        WHEN '팩토링수수료' THEN 'This is a fee paid to financial companies in accounts receivable factoring transactions.'
        WHEN '계정대체' THEN 'It is an accounting process that moves accounting items to other accounts and classifies them.'
        WHEN '전표' THEN 'It is a unit of evidence prepared to record transaction details in the accounting ledger.'
        WHEN '수정분개' THEN 'This is an accounting journal that is additionally recorded to correct errors or adjust period attribution.'
        WHEN '결산조정' THEN 'This is an adjustment to appropriately reflect revenue, expenses, assets and liabilities at the end of the accounting period.'
        WHEN '감사조정' THEN 'This is an accounting adjustment that reflects the auditor''s comments during the external audit process.'
        WHEN '세무조정' THEN 'This is the process of adjusting accounting profits to change them into taxable income under tax law.'
        WHEN '데이터카탈로그' THEN 'This is a list of data set location structure description owner quality information.'
        WHEN '메타데이터레지스트리' THEN 'It is a repository that consistently manages metadata of data and terms.'
        WHEN '용어사전버전' THEN 'This value represents the distribution or management version of dictionary data.'
        WHEN '사전배포' THEN 'This is a task that reflects the reviewed preliminary data into the operating environment.'
        WHEN '사전롤백' THEN 'This is the process of reverting a pre-release that had problems to the previous version.'
        WHEN '사전스냅샷' THEN 'This is data stored by fixing the dictionary term and alias status at a specific point in time.'
        WHEN '사전검증리포트' THEN 'This report summarizes the results of preliminary quality verification, including duplicate omissions and reference errors.'
        WHEN '용어소유자' THEN 'A person or team responsible for defining and maintaining specific terms.'
        WHEN '출처검증자' THEN 'This person is in charge of reviewing the source of terms and appropriateness of definitions.'
        WHEN '검토승인자' THEN 'This person is responsible for final approval of term disclosure or import.'
        WHEN '변경요청' THEN 'Term Definition A request to register when alias source metadata needs to be modified.'
        WHEN '변경이력' THEN 'This is a record of when and how terms and aliases changed.'
        WHEN '품질점수' THEN 'Term Definition Source This is a quality indicator calculated by combining alias status.'
        WHEN '신뢰도점수' THEN 'This is a score that reflects the source and review status and indicates the level of trust in the term.'
        WHEN '검토우선순위' THEN 'This is a priority that determines which term candidates will be reviewed first.'
        WHEN '자동추출후보' THEN 'These are term candidate items automatically extracted from documents or data.'
        WHEN '수동등록용어' THEN 'This is a terminology item registered directly by the administrator.'
        WHEN '외부수집용어' THEN 'These are terminology items collected from official institutions or financial company data.'
        WHEN '내부정의용어' THEN 'This is a terminology item defined by QAIMA service and analysis logic.'
        WHEN '검토반려' THEN 'The term candidate has been rejected because it does not meet quality or source standards.'
        WHEN '검토보류' THEN 'Because additional confirmation is needed, the decision to disclose the term has been postponed.'
        WHEN '승인대기' THEN 'The review has been completed, but not before final approval.'
        WHEN '공개대기' THEN 'The approved term is awaiting operational deployment.'
        WHEN '검색로그' THEN 'This is a log that records search terms entered by users and clicks on results.'
        WHEN '무결과검색어' THEN 'This is a user search term for which there were no search results.'
        WHEN '검색클릭률' THEN 'This is the rate at which users click after search results are displayed.'
        WHEN '자동완성클릭률' THEN 'The percentage of autocomplete suggestions selected.'
        WHEN '검색전환율' THEN 'This is the percentage that leads to the target action after the search.'
        WHEN '인기검색어' THEN 'This is a search term that has been entered a lot over a certain period of time.'
        WHEN '검색어군집' THEN 'This is a group of search terms with similar intent or expression.'
        WHEN 'alias추천' THEN 'This function suggests aliases to attach to canonical terms in search logs or documents.'
        WHEN '용어추천' THEN 'This function suggests new canonical term candidates in documents or user searches.'
        WHEN '정의품질' THEN 'This is a quality level that indicates whether the terminology explanation is accurate, concise, and consistent with the source.'
        WHEN '출처누락' THEN 'The source organization or URL information required for the term is empty.'
        WHEN 'alias누락' THEN 'Synonyms frequently used by users are not registered as aliases.'
        WHEN '대표어충돌' THEN 'Terms with different meanings are managed as the same canonical term and are in conflict.'
        WHEN '동음이의어' THEN 'These are terms with the same notation but different meanings.'
        WHEN '의미중복' THEN 'Terms with different expressions but almost the same actual meaning are being managed redundantly.'
        WHEN '용어분리' THEN 'This is the process of dividing a term that was managed as one into several canonical terms according to differences in meaning.'
        WHEN '용어대체' THEN 'This is the task of changing existing terms into more appropriate canonical terms.'
        WHEN '검색품질평가' THEN 'This is a process to evaluate how well the search results match the user''s intent.'
        WHEN '무결과율' THEN 'This is the percentage of searches with no results among all searches.'
        WHEN '상위노출률' THEN 'This is the rate at which a specific item is exposed in the top position among search results.'
        WHEN '클릭순위' THEN 'This is the exposure ranking of the search results clicked by the user.'
        WHEN '검색재시도율' THEN 'This is the percentage of users changing their search term and searching again after the first search.'
        WHEN '검색세션' THEN 'It is a unit that combines search actions performed continuously by a user.'
        WHEN '검색의도' THEN 'This is the purpose of the information the user is trying to find by entering a search term.'
        WHEN '의도분류' THEN 'This is the task of classifying search words or documents into categories according to purpose.'
        WHEN '추천근거' THEN 'This is information that explains why the recommended term or alias was selected.'
        WHEN '검토샘플' THEN 'These are selected from the entire data and subject to review to check quality.'
        WHEN '샘플링오류' THEN 'This error occurs when the review sample does not properly represent the overall data characteristics.'
        WHEN '정합성검사' THEN 'This is a test to check whether data values ​​linked to each other are logically correct.'
        WHEN '참조무결성' THEN 'Like alias and canonical terms, this is a property in which the reference relationship is maintained without being broken.'
        WHEN '스키마검증' THEN 'This is a check to check whether the CSV or database column structure matches the expected format.'
        WHEN '필수값검증' THEN 'This is a check to check whether essential values ​​such as term description status are empty.'
        WHEN '정규화충돌' THEN 'This is a phenomenon where different original texts overlap with the same key after adjusting the case and spaces.'
        WHEN '일괄업서트' THEN 'This is the process of inserting or updating multiple terms or aliases at once.'
        WHEN '부분실패' THEN 'During mass processing, only some items fail, while others succeed.'
        WHEN '실패라인' THEN 'This is the row number or row data where an error occurred during CSV import.'
        WHEN '재처리대상' THEN 'This is an item that has failed or been put on hold and needs to be re-imported or reviewed.'
        WHEN '검증체크섬' THEN 'This is a summary value calculated to check whether the file contents are the same as before.'
        WHEN '배포체크리스트' THEN 'This is a list of items to check before reflecting preliminary data in operations.'
        WHEN '매입채무회전율' THEN 'It is an efficiency indicator that shows how frequently accounts payable are paid over a certain period of time.'
        WHEN 'EBIT마진' THEN 'It is a ratio that compares EBIT to sales to look at profit-generating power related to operations.'
        WHEN '순차입금비율' THEN 'It is a ratio that compares net borrowings to capital or assets to determine financial burden.'
        WHEN '자본잠식률' THEN 'It is used to judge financial risk based on the ratio of eroded capital to capital.'
        WHEN '영업현금흐름전환율' THEN 'It is a ratio that measures the degree to which accounting profits are converted into actual operating cash flow.'
        WHEN '잉여현금흐름마진' THEN 'It is an indicator of cash generation efficiency by comparing free cash flow to sales.'
        WHEN 'ROE분해' THEN 'This is a method of analyzing ROE by dividing it into components such as profitability, efficiency, and leverage.'
        WHEN '듀퐁분석' THEN 'It is an analysis system that decomposes ROE into net profit ratio, asset turnover ratio, and financial leverage.'
        WHEN '재투자율' THEN 'It is the proportion of earned profits or cash flow that is put back into the business.'
        WHEN '차입금상환능력' THEN 'It is the ability of a company to repay loans with operating cash flow and cash reserves.'
        WHEN '현금보유비율' THEN 'It is a ratio that represents the level of cash equivalents held compared to total assets or market capitalization.'
        WHEN '자산건전성' THEN 'It refers to the possibility and stability that assets will be recovered normally without becoming insolvent.'
        WHEN '부실여신' THEN 'These are loans in which principal and interest repayment is delayed or the possibility of recovery is low.'
        WHEN '대손충당금전입액' THEN 'This amount reflects the increase in expected non-recoverable amount as a cost.'
        WHEN '연체채권' THEN 'This is a bond that has exceeded the agreed upon repayment deadline.'
        WHEN '차환위험' THEN 'This is the risk of not being able to repay or extend maturing debt with new borrowing.'
        WHEN '만기구조' THEN 'It is a structure that shows how the maturity of assets or liabilities is distributed by period.'
        WHEN '금리재설정주기' THEN 'This is the cycle in which the interest rates applied to variable rate products are re-determined.'
        WHEN '고정금리부채' THEN 'It is a debt whose interest rate does not change during maturity or contract period.'
        WHEN '변동금리부채' THEN 'It is a debt whose interest rate changes depending on changes in the base interest rate.'
        WHEN '환율민감도' THEN 'This is the sensitivity of exchange rate changes to sales, costs, profits, and asset values.'
        WHEN '원자재민감도' THEN 'It is the sensitivity of changes in raw material prices to corporate costs and profits.'
        WHEN '매출집중도' THEN 'It indicates how concentrated sales are in a specific customer product region.'
        WHEN '고객집중도' THEN 'This is the proportion of total sales that a small number of customers account for.'
        WHEN '공급망위험' THEN 'Disruption in the supply of raw materials, parts, and logistics is a risk to corporate activities.'
        WHEN '지속가능성공시' THEN 'This is a disclosure that informs investors of a company''s environmental, social and governance-related risks and performance.'
        WHEN 'ESG공시' THEN 'This is a disclosure that discloses environmental, social and governance information so that it can be used in investment decisions.'
        WHEN '기후공시' THEN 'This is the sustainability disclosure area that explains climate change-related risks, opportunities, and emissions targets.'
        WHEN '온실가스배출량' THEN 'This is the total amount of greenhouse gases emitted from corporate activities.'
        WHEN '직접배출' THEN 'These are greenhouse gas emissions generated directly from facilities owned or controlled by a company.'
        WHEN '간접배출' THEN 'These are greenhouse gas emissions generated indirectly through the use of purchased electricity, heat, steam, etc.'
        WHEN '기타간접배출' THEN 'These are indirect emissions generated in the value chain, such as business trip logistics at the supply chain use stage.'
        WHEN '탄소중립' THEN 'The goal is to set net emissions to zero by offsetting greenhouse gas emissions through reduction and absorption.'
        WHEN '넷제로' THEN 'It is a state or goal of reducing net greenhouse gas emissions to zero.'
        WHEN 'RE100' THEN 'It is a global initiative for companies to convert the power they use to renewable energy.'
        WHEN '재생에너지' THEN 'It refers to energy sources that are naturally replenished, such as solar power, wind power, and hydropower.'
        WHEN '탄소가격' THEN 'It is a price imposed on carbon emissions or established in the market.'
        WHEN '배출권거래제' THEN 'It is a system that allocates greenhouse gas emissions rights and allows them to be traded in the market.'
        WHEN '배출권' THEN 'It is the right to emit a certain amount of greenhouse gases.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '대차체결량',
        '대차상환량',
        '차입잔고',
        '매수압력',
        '매도압력',
        '호가흡수',
        '유동성공백',
        '틱데이터',
        '분봉',
        '일봉',
        '주봉',
        '월봉',
        '거래량급증',
        '거래대금급증',
        '신고가',
        '신저가',
        '갭상승',
        '갭하락',
        '장대양봉',
        '장대음봉',
        '추세전환',
        '상방돌파',
        '하방이탈',
        '지지선',
        '저항선',
        '개인형IRP',
        '기업형IRP',
        '퇴직IRP',
        '연금계좌',
        '연금저축계좌',
        '연금저축신탁',
        '연금계좌이전',
        '연금납입한도',
        '연금수령한도',
        '연금개시연령',
        '연금계좌해지',
        'ISA납입한도',
        'ISA비과세한도',
        'ISA의무가입기간',
        'ISA중도해지',
        '서민형ISA',
        '일반형ISA',
        '농어민형ISA',
        '발행어음약정',
        '약정형RP',
        '수시형RP',
        '자동매수형RP',
        '외화MMF',
        '달러MMF',
        '국내채권',
        '해외채권',
        '달러표시채권',
        '변동금리채권',
        '물가연동채권',
        '신용연계채권',
        '구조화채권',
        '은행채',
        '여전채',
        '카드채',
        '캐피탈채',
        '금융채',
        '후순위금융채',
        '신탁형ISA보수',
        '일임형ISA보수',
        '투자일임수수료',
        '투자자문수수료',
        '랩수수료',
        '성과연동보수',
        '환매제한펀드',
        '개방형펀드',
        '폐쇄형펀드',
        '상장폐쇄형펀드',
        '순매출',
        '총매출',
        '매출할인',
        '매출반품',
        '매출에누리',
        '순매출총이익률',
        '매출원가구성비',
        '원재료비',
        '노무비',
        '제조경비',
        '제조간접비',
        '감가상각방법',
        '정액법',
        '정률법',
        '생산량비례법',
        '매출채권담보차입',
        '재고자산담보차입',
        '운전자금대출',
        '시설자금대출',
        '매출채권보험',
        '팩토링수수료',
        '계정대체',
        '전표',
        '수정분개',
        '결산조정',
        '감사조정',
        '세무조정',
        '데이터카탈로그',
        '메타데이터레지스트리',
        '용어사전버전',
        '사전배포',
        '사전롤백',
        '사전스냅샷',
        '사전검증리포트',
        '용어소유자',
        '출처검증자',
        '검토승인자',
        '변경요청',
        '변경이력',
        '품질점수',
        '신뢰도점수',
        '검토우선순위',
        '자동추출후보',
        '수동등록용어',
        '외부수집용어',
        '내부정의용어',
        '검토반려',
        '검토보류',
        '승인대기',
        '공개대기',
        '검색로그',
        '무결과검색어',
        '검색클릭률',
        '자동완성클릭률',
        '검색전환율',
        '인기검색어',
        '검색어군집',
        'alias추천',
        '용어추천',
        '정의품질',
        '출처누락',
        'alias누락',
        '대표어충돌',
        '동음이의어',
        '의미중복',
        '용어분리',
        '용어대체',
        '검색품질평가',
        '무결과율',
        '상위노출률',
        '클릭순위',
        '검색재시도율',
        '검색세션',
        '검색의도',
        '의도분류',
        '추천근거',
        '검토샘플',
        '샘플링오류',
        '정합성검사',
        '참조무결성',
        '스키마검증',
        '필수값검증',
        '정규화충돌',
        '일괄업서트',
        '부분실패',
        '실패라인',
        '재처리대상',
        '검증체크섬',
        '배포체크리스트',
        '매입채무회전율',
        'EBIT마진',
        '순차입금비율',
        '자본잠식률',
        '영업현금흐름전환율',
        '잉여현금흐름마진',
        'ROE분해',
        '듀퐁분석',
        '재투자율',
        '차입금상환능력',
        '현금보유비율',
        '자산건전성',
        '부실여신',
        '대손충당금전입액',
        '연체채권',
        '차환위험',
        '만기구조',
        '금리재설정주기',
        '고정금리부채',
        '변동금리부채',
        '환율민감도',
        '원자재민감도',
        '매출집중도',
        '고객집중도',
        '공급망위험',
        '지속가능성공시',
        'ESG공시',
        '기후공시',
        '온실가스배출량',
        '직접배출',
        '간접배출',
        '기타간접배출',
        '탄소중립',
        '넷제로',
        'RE100',
        '재생에너지',
        '탄소가격',
        '배출권거래제',
        '배출권'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '탄소배출권' THEN 'These are emission rights traded in response to greenhouse gas emissions.'
        WHEN '상쇄배출권' THEN 'This is an emission credit that has been recognized as a reduction project and can be used to offset emissions.'
        WHEN '녹색채권' THEN 'This is a bond issued for the purpose of financing eco-friendly projects.'
        WHEN '사회적채권' THEN 'This is a bond issued to finance social value creation projects.'
        WHEN '지속가능채권' THEN 'This bond is issued to support both environmental and social projects.'
        WHEN '지속가능연계채권' THEN 'It is a bond whose terms vary depending on whether the issuer achieves its sustainability goals.'
        WHEN '녹색분류체계' THEN 'This is a classification standard for determining whether economic activities are eco-friendly.'
        WHEN 'K택소노미' THEN 'This is an expression referring to the Korean green classification system.'
        WHEN '그린워싱' THEN 'This is an act of displaying eco-friendliness in a way that is exaggerated or misleading.'
        WHEN '중대성평가' THEN 'This is an evaluation that selects important ESG issues that a company needs to disclose or manage.'
        WHEN '이중중대성' THEN 'It is a concept that evaluates importance from two perspectives: corporate value impact and social and environmental impact.'
        WHEN '공급망실사' THEN 'This is a procedure to check human rights and environmental risks that may occur in partners and supply chains.'
        WHEN '인권실사' THEN 'It is a process to identify and mitigate risks of human rights violations in corporate activities and supply chains.'
        WHEN '지속가능경영위원회' THEN 'This is a committee that handles ESG and sustainable management issues inside and outside the board of directors.'
        WHEN '사외이사후보추천위원회' THEN 'This is a committee within the board of directors that screens and recommends outside director candidates.'
        WHEN '보상위원회' THEN 'This is a committee that reviews executive compensation and performance compensation systems.'
        WHEN '내부거래위원회' THEN 'This is a committee that reviews the appropriateness of related party transactions and internal transactions.'
        WHEN '감사위원분리선출' THEN 'This is a system in which directors who become members of the audit committee are appointed separately from other directors.'
        WHEN '집중투표청구' THEN 'This is the exercise of the right of minority shareholders to request a concentrated vote in the election of directors.'
        WHEN '주주제안권' THEN 'This is the right for shareholders to propose agenda items for general shareholders’ meetings if they meet certain requirements.'
        WHEN '의결권자문사' THEN 'This is a company that provides institutional investors with analysis of agenda items at general shareholders'' meetings and opinions on voting rights.'
        WHEN '스튜어드십코드' THEN 'These are principles and guidelines for institutional investors to fulfill their fiduciary responsibilities.'
        WHEN '주주행동주의' THEN 'It is a movement in which shareholders actively seek to increase corporate value or improve governance.'
        WHEN '주주서한' THEN 'It is a document through which shareholders convey their opinions and demands to company management or the board of directors.'
        WHEN '위임장대결' THEN 'Management and shareholders are competing to collect proxies to secure voting rights at the general shareholders'' meeting.'
        WHEN '의안분석' THEN 'This is an analysis that examines the content and impact of the shareholders’ meeting agenda.'
        WHEN '찬반권고' THEN 'This is a recommendation in which a voting rights advisor, etc. presents an opinion for or against the agenda of a general shareholders'' meeting.'
        WHEN '배당정책' THEN 'It is a policy that sets the standards and direction for a company to distribute profits to shareholders.'
        WHEN '자사주정책' THEN 'These are the company''s standards and direction for the acquisition, disposal, and cancellation of treasury stocks.'
        WHEN '주주환원정책' THEN 'It is a policy to return profits to shareholders through dividends and share purchase cancellation.'
        WHEN '총주주환원율' THEN 'It is a ratio that represents the level of return compared to net profit or market capitalization by combining dividends and share buybacks.'
        WHEN '자본배분' THEN 'It is a decision by a company to divide capital into investments, dividends, treasury stock mergers and acquisitions, and debt repayment.'
        WHEN '재무정책' THEN 'It is the direction of a company''s financial decisions, such as borrowing, dividends, investments, and cash holdings.'
        WHEN '오버행' THEN 'The potential sale volume remains a burden on the market.'
        WHEN '보호예수해제' THEN 'This means that stocks subject to sales restrictions have expired and can now be sold.'
        WHEN '락업해제' THEN 'This means that restrictions on the sale of stocks that were subject to mandatory holding or protected deposit will be lifted.'
        WHEN '의무보유해제' THEN 'The restriction on mandatory holding of stocks that could not be sold for a certain period of time has ended.'
        WHEN '구주매출' THEN 'This is a method in which existing shareholders sell their shares during a public offering.'
        WHEN '신주모집' THEN 'This is a method by which a company solicits newly issued stocks from investors.'
        WHEN '공모구조' THEN 'This is a method of structuring the public offering volume, such as offering new shares and selling old shares.'
        WHEN '상장주선인' THEN 'It is a financial investment company that supports and supervises issuing companies during the initial public offering or listing process.'
        WHEN '대표주관회사' THEN 'It is the lead securities company that oversees the public offering and listing procedures.'
        WHEN '인수단' THEN 'It is a group of financial investment companies that underwrite securities issuance or arrange public offering sales.'
        WHEN '인수수수료' THEN 'This is a fee paid for underwriting and underwriting securities issuance.'
        WHEN '초과배정옵션' THEN 'This is an option that is used to stabilize the market after allocating in excess of the public offering amount.'
        WHEN '그린슈옵션' THEN 'This is a practical expression meaning an over-allocation option.'
        WHEN '가격안정조치' THEN 'It is a market stabilization measure that the organizer can take to alleviate sudden price fluctuations in the initial stage of listing.'
        WHEN '의무인수분' THEN 'This is the quantity of stocks that the listing arranger must acquire or hold in accordance with regulations.'
        WHEN '수요예측경쟁률' THEN 'It is a ratio that indicates how much the volume of institutional demand forecast applications is compared to the volume of public offerings.'
        WHEN '확약물량' THEN 'This is the amount of allocation that institutional investors have confirmed not to sell for a certain period of time.'
        WHEN '미확약물량' THEN 'This is the amount of public offering shares allocated without mandatory holding commitments.'
        WHEN '균등배정수량' THEN 'This is the number of shares allocated to investors through equal allocation.'
        WHEN '비례배정수량' THEN 'It is the number of shares allocated in proportion to the subscription margin or application quantity.'
        WHEN '배정확률' THEN 'It is the possibility that the subscriber will actually be allocated the stocks he/she wants.'
        WHEN '청약단위' THEN 'This is the minimum or step-by-step quantity unit that can be applied for in public offering or paid-in capital increase subscription.'
        WHEN '청약우대조건' THEN 'This is a condition that must be met to receive preferential treatment in the subscription limit or allocation of public offering shares.'
        WHEN '청약수수료' THEN 'This is a fee paid to securities companies when applying for subscriptions for public stocks, etc.'
        WHEN '환불금' THEN 'This is the margin refunded for the quantity not allocated after subscription.'
        WHEN '추가납입' THEN 'This is a procedure to pay additional subscription money depending on the allocation results.'
        WHEN '장외파생상품' THEN 'It is a derivative product traded through a contract between the parties over the counter, not on an exchange.'
        WHEN '금리스왑' THEN 'It is a derivative contract that exchanges fixed-rate and floating-rate cash flows.'
        WHEN '통화스왑거래' THEN 'It is a derivative transaction that exchanges principal and interest of different currencies.'
        WHEN '총수익스왑' THEN 'It is a swap in which one party pays the total return on the underlying asset and the other party pays the agreed interest.'
        WHEN '신용부도스왑' THEN 'It is a derivative contract that agrees to pay premiums and compensation to transfer the risk of default.'
        WHEN '선도금리계약' THEN 'It is an over-the-counter derivative contract that pre-determines the interest rate for a certain period of time in the future.'
        WHEN '오버나이트인덱스스왑' THEN 'It is a swap that exchanges fixed interest rates and floating interest rates based on the overnight interest rate index.'
        WHEN '변동성스왑' THEN 'It is a derivative contract that is settled based on the difference between the realized volatility of the underlying asset and the contracted volatility.'
        WHEN '분산스왑' THEN 'It is a derivative contract that is settled based on the difference between the realized variance and the agreed variance of the underlying asset rate of return.'
        WHEN '배리어옵션' THEN 'It is an option whose rights arise or expire when the price of the underlying asset reaches a certain barrier.'
        WHEN '디지털옵션' THEN 'This is an option that pays a pre-determined fixed amount when conditions are met.'
        WHEN '아시아옵션' THEN 'This is an option whose payoff is determined based on the average price of the underlying asset.'
        WHEN '스왑션' THEN 'This is an option that gives the right to enter into a swap contract in the future.'
        WHEN '캡옵션' THEN 'It is an option-type derivative product with an interest rate cap set to limit the burden caused by rising interest rates.'
        WHEN '플로어옵션' THEN 'It is an option-type derivative product that sets a lower interest rate limit to limit the decrease in profits due to falling interest rates.'
        WHEN '칼라' THEN 'It is a structure that limits the range of interest rate fluctuations by combining a cap option and a floor option.'
        WHEN '선물환포인트' THEN 'It is a value expressed in points as the difference between the forward exchange rate and the spot exchange rate.'
        WHEN '스왑레이트' THEN 'In a foreign exchange swap, it is an annualized ratio of the difference between the forward exchange rate and the spot exchange rate.'
        WHEN '크로스커런시베이시스' THEN 'The basis reflects the difference in procurement costs between the two currencies in the currency swap market.'
        WHEN '달러조달비용' THEN 'This includes costs incurred when raising dollar funds, including interest rates and swap costs.'
        WHEN '외화자금시장' THEN 'It is a market where foreign currency short-term funds are traded and raised.'
        WHEN '역외시장' THEN 'It is a market where domestic currency or financial products are traded outside of domestic regulatory jurisdiction.'
        WHEN '역내시장' THEN 'It is a market where financial products are traded within the domestic system and payment system.'
        WHEN '국제금융시장' THEN 'It is a financial market where financing and investment occur across borders.'
        WHEN '글로벌유동성' THEN 'It refers to the funding conditions and level of liquidity available in the international financial markets.'
        WHEN '긴축금융환경' THEN 'Financing conditions have become stricter due to rising interest rates and reduced credit.'
        WHEN '완화금융환경' THEN 'Funding conditions have become loose due to falling interest rates and liquidity supply.'
        WHEN '금융여건지수' THEN 'It is an index that represents the financial environment by combining interest rates, exchange rates, stock prices, and credit spreads.'
        WHEN '실질구매력' THEN 'It is the amount of goods and services that can actually be purchased, reflecting the price level.'
        WHEN '구매력평가' THEN 'It is a theory that explains the appropriate level of exchange rate based on the price of the same bundle of products.'
        WHEN '빅맥지수' THEN 'It is an indicator that compares the relative purchasing power of currencies using the price of a Big Mac.'
        WHEN '임금피크제' THEN 'It is a system that maintains employment by adjusting wages after a certain age.'
        WHEN '노동참가율' THEN 'It is the ratio of the working age population participating in economic activities.'
        WHEN '청년실업률' THEN 'This is the proportion of the unemployed among the youth economically active population.'
        WHEN '고용비용지수' THEN 'It is an index that indicates changes in the cost a company incurs to hire workers.'
        WHEN '실질구매력임금' THEN 'It is a wage that represents actual purchasing power by adjusting nominal wages to the price level.'
        WHEN '단위임금비용' THEN 'It is the wage cost paid per unit of output.'
        WHEN '생산성격차' THEN 'This is the difference in productivity levels between countries or industrial companies.'
        WHEN '부가가치율' THEN 'It is the ratio of added value to total output.'
        WHEN '투입산출표' THEN 'This is a statistical table that organizes the flow of goods and services between industries in matrix form.'
        WHEN '산업연관표' THEN 'This is a national account statistical table showing the production inducement and input structure between industries.'
        WHEN '자금순환표' THEN 'This is a statistical table showing the flow of funds and operations between economic entities.'
        WHEN '국민대차대조표' THEN 'This is a statistical table showing the total assets, liabilities, and net worth of the country at each point in time.'
        WHEN '플랫폼매출' THEN 'This is revenue generated from brokerage fees, advertising subscriptions, etc. in the platform business.'
        WHEN '거래액' THEN 'This is the total amount of goods and services traded on a platform or commerce.'
        WHEN '총거래액' THEN 'This is the total transaction amount that occurred on the platform over a certain period of time.'
        WHEN '순거래액' THEN 'This is the actual transaction amount after deducting cancellation, refund, return, etc.'
        WHEN '테이크레이트' THEN 'This is the percentage of the transaction amount that the platform takes as commission or sales.'
        WHEN '활성이용자' THEN 'This is the number of users who actually used the service over a certain period of time.'
        WHEN '월간활성이용자' THEN 'This is the number of unique users who used the service in one month.'
        WHEN '일간활성이용자' THEN 'This is the number of unique users who used the service in one day.'
        WHEN '유료전환율' THEN 'This is the percentage of free users converted to paid customers.'
        WHEN '재구매율' THEN 'This is the ratio of customers who purchased again among customers who purchased it again.'
        WHEN '객단가' THEN 'This is the average payment amount per customer or per order.'
        WHEN '주문건수' THEN 'This is the total number of orders that occurred during a certain period of time.'
        WHEN '취소율' THEN 'This is the percentage of orders or contracts that are canceled.'
        WHEN '환불률' THEN 'This is the percentage of sales or orders that were refunded.'
        WHEN '매출채널' THEN 'This is the path through which products or services are sold.'
        WHEN '직판매출' THEN 'This is revenue generated by the company selling directly to customers.'
        WHEN '대리점매출' THEN 'This is sales generated through dealers or distribution partners.'
        WHEN '해외매출' THEN 'This is sales generated from overseas customers or overseas corporations.'
        WHEN '내수매출' THEN 'This is sales generated in the domestic market.'
        WHEN '제품믹스' THEN 'This is the composition of the sales proportion of various product groups.'
        WHEN '가격믹스' THEN 'This is the sales composition and changes by sales price range.'
        WHEN '물량효과' THEN 'This is the effect that changes in sales volume have on sales or profits.'
        WHEN '가격효과' THEN 'This is the effect that a change in selling price has on sales or profits.'
        WHEN '환율효과' THEN 'This is the effect of exchange rate fluctuations on sales costs and profits.'
        WHEN '원가절감효과' THEN 'Cost reduction activities contributed to profit improvement.'
        WHEN '영업레버리지효과' THEN 'Due to the fixed cost structure, changes in sales are reflected more significantly in operating profit.'
        WHEN '변동비율' THEN 'It is the ratio of variable costs to sales.'
        WHEN '고정비율' THEN 'It is the ratio of fixed costs to sales or total costs.'
        WHEN '기여이익률' THEN 'It is a ratio comparing contribution margin to sales.'
        WHEN '데이터드리븐랭킹' THEN 'It is a method of searching or ranking stocks by combining data signals and rules.'
        WHEN '랭킹실험' THEN 'This is an experiment to compare the performance of search or recommendation ranking formulas.'
        WHEN '온라인평가' THEN 'This is a method of evaluating model or ranking quality through user responses in an operating environment.'
        WHEN '오프라인평가' THEN 'This is a method of evaluating model or ranking quality using past data and a set of correct answers.'
        WHEN '정답셋' THEN 'It is a set of correct results predetermined for evaluation.'
        WHEN '평가쿼리' THEN 'This is a set of representative search terms used to evaluate search quality.'
        WHEN '관련도등급' THEN 'This value indicates how related the search results are to the query intent.'
        WHEN '평균정밀도' THEN 'This is an indicator that evaluates on average how well related results are placed at the top.'
        WHEN '정규화할인누적이득' THEN 'It is a ranking evaluation indicator that reflects both search result ranking and relevance level.'
        WHEN '상호순위평균' THEN 'This is an indicator that evaluates how high the first related result is.'
        WHEN '히트율' THEN 'This is the percentage of desired items included in recommendations or search results.'
        WHEN '커버리지율' THEN 'It is a ratio that indicates how wide a range of total candidates the recommendation or search system covers.'
        WHEN '신선도점수' THEN 'This score indicates how recent the data or results reflect the most recent information.'
        WHEN '다양성점수' THEN 'This score indicates how much search or recommendation results include different topics and scopes.'
        WHEN '중복노출률' THEN 'This is the rate at which items with the same meaning are repeatedly displayed in search results.'
        WHEN 'alias확장률' THEN 'It is the ratio of the number of registered aliases compared to canonical terms.'
        WHEN '검색실패샘플' THEN 'This is a case where the desired results were not found in the search quality evaluation.'
        WHEN '오탐검색결과' THEN 'This is an item that does not match the search intent but is exposed as a result.'
        WHEN '누락검색결과' THEN 'This item matches the search intent but is not included in the results.'
        WHEN '랭킹회귀' THEN 'This is a phenomenon in which the quality of the results has become worse than before after changing the rankings.'
        WHEN '랭킹가드레일' THEN 'This is a constraint that ensures that changes in search ranking do not break the minimum quality standards.'
        WHEN '사전커버리지목표' THEN 'This is a goal set for the scope and quantity of terms required for operation.'
        WHEN 'alias커버리지목표' THEN 'This is the goal set for the level of alias reinforcement for each canonical term.'
        WHEN '채권듀레이션' THEN 'It is an indicator that shows how sensitive bond prices are to changes in interest rates in terms of period.'
        WHEN '맥컬리듀레이션' THEN 'It is a duration indicator that represents the present value weighted average maturity of bond cash flows.'
        WHEN '유효듀레이션' THEN 'This is an indicator that estimates the interest rate sensitivity of bonds with embedded options based on price scenarios.'
        WHEN '컨벡시티' THEN 'It is a bond risk indicator that represents the curvature of the relationship between interest rate changes and bond price changes.'
        WHEN '음의컨벡시티' THEN 'When interest rates change, bond prices rise and fall in an asymmetrically unfavorable manner.'
        WHEN '경상수익률' THEN 'It is a yield calculated by dividing a bond''s annual interest income by its current price.'
        WHEN '채권총수익률' THEN 'It is the rate of return on bond investment that reflects both interest income and price fluctuation gains and losses.'
        WHEN '채권가격민감도' THEN 'This is the extent to which bond prices change in response to changes in interest rates or spreads.'
        WHEN '수익률곡선위험' THEN 'This is the risk that the value of the bond portfolio will fluctuate due to changes in the interest rate structure by maturity.'
        WHEN '캐리수익' THEN 'This is the holding profit generated from the difference in interest and financing costs during the bond holding period.'
        WHEN '신용스프레드듀레이션' THEN 'It is an indicator that measures bond price sensitivity to changes in credit spread.'
        WHEN '제로볼래틸리티스프레드' THEN 'This is a value that adds a certain spread to the spot yield curve so that the present value of bond cash flow is equal to the price.'
        WHEN '자산스왑스프레드' THEN 'This is the spread obtained compared to the base interest rate when bonds and interest rate swaps are combined.'
        WHEN '채권스프레드' THEN 'This difference indicates the degree to which bond yields are higher than the benchmark interest rate or government bond yield.'
        WHEN '크레딧베타' THEN 'Credit spread is the sensitivity of individual bonds or portfolios to market fluctuations.'
        WHEN '기대손실' THEN 'This is the average credit loss expected considering default probability loss rate exposure.'
        WHEN '부도시손실률' THEN 'This is the percentage of losses that are not expected to be recovered in the event of bankruptcy.'
        WHEN '부도노출액' THEN 'This is the amount of the bond expected to be exposed to the risk of loss at the time of default.'
        WHEN '예상신용손실' THEN 'It is an estimated credit loss recognized in accounting that reflects the possibility of future default and loss rate.'
        WHEN '신용손실충당금' THEN 'This is a provision set against expected credit losses on loan receivables or financial assets.'
        WHEN '신용등급전망' THEN 'It is a judgment that the credit rating agency suggests that the direction of future rating changes will be stable, positive, negative, etc.'
        WHEN '신용등급감시' THEN 'This is a condition that raters are especially observing because there is a possibility that the credit rating will change in a short period of time.'
        WHEN '등급상향' THEN 'This is when a credit rating agency adjusts the credit rating of an issuer or bond to a higher level.'
        WHEN '등급하향' THEN 'This is when a credit rating agency adjusts the credit rating of an issuer or bond to a lower level.'
        WHEN '담보회수율' THEN 'This is the ratio at which loan or bond losses can be recovered through the disposal of collateral.'
        WHEN '부실채권비율' THEN 'This is the proportion of amounts classified as non-performing out of all loans or receivables.'
        WHEN '무수익여신비율' THEN 'This is the ratio of loans that do not normally generate interest income.'
        WHEN '여신한도' THEN 'This is the maximum loan amount approved by a financial company to be provided to a borrower.'
        WHEN '대출약정한도' THEN 'This is the total limit agreed upon for use by the borrower in the loan contract.'
        WHEN '미사용여신한도' THEN 'This is the amount of the approved credit limit that has not yet been actually used.'
        WHEN '한도대출' THEN 'It is a loan method in which the required amount is withdrawn and repaid at any time within a set limit.'
        WHEN '마이너스통장' THEN 'This is a limit loan that can be used to reduce the balance to negative by assigning a loan limit to the deposit/withdrawal account.'
        WHEN '만기연장' THEN 'This is a measure to delay the repayment date of a loan or bond beyond the original maturity date.'
        WHEN '대환대출' THEN 'This is a transaction that involves switching to a new loan to repay an existing loan.'
        WHEN '기한이익상실' THEN 'The debtor loses the right to postpone repayment until maturity due to breach of contract, etc.'
        WHEN '채무불이행' THEN 'The debtor has failed to pay the agreed principal and interest or fulfill contractual obligations.'
        WHEN '담보권실행' THEN 'This is the process of collecting the debt by disposing of the collateral in case of default.'
        WHEN '근저당권' THEN 'It is a mortgage established to secure claims arising from ongoing transactions up to a certain limit.'
        WHEN '질권설정' THEN 'It is an act of establishing a pledge to provide property rights, such as bonds or deposits, as collateral.'
        WHEN '보증채무' THEN 'This is a debt that is borne by the guarantor on behalf of the principal debtor if he or she fails to repay.'
        WHEN '연대보증' THEN 'It is a guarantee method in which the guarantor is obligated to repay the debt with the same responsibility as the main debtor.'
        WHEN '담보가치평가' THEN 'This is a procedure to calculate the loan amount by evaluating the market value and disposal possibilities of the collateral.'
        WHEN '담보보전비율' THEN 'It is a ratio that indicates whether the collateral value is sufficiently maintained compared to the loan balance.'
        WHEN '차주신용평가' THEN 'It is a procedure to evaluate credit risk based on the borrower’s income, financial status, and repayment history.'
        WHEN '내부등급법' THEN 'This is a method by which a financial company calculates risk-weighted assets using an internal credit rating model.'
        WHEN '표준방법' THEN 'This is a method of calculating risk-weighted assets using risk weights and standards set by the supervisory authorities.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '탄소배출권',
        '상쇄배출권',
        '녹색채권',
        '사회적채권',
        '지속가능채권',
        '지속가능연계채권',
        '녹색분류체계',
        'K택소노미',
        '그린워싱',
        '중대성평가',
        '이중중대성',
        '공급망실사',
        '인권실사',
        '지속가능경영위원회',
        '사외이사후보추천위원회',
        '보상위원회',
        '내부거래위원회',
        '감사위원분리선출',
        '집중투표청구',
        '주주제안권',
        '의결권자문사',
        '스튜어드십코드',
        '주주행동주의',
        '주주서한',
        '위임장대결',
        '의안분석',
        '찬반권고',
        '배당정책',
        '자사주정책',
        '주주환원정책',
        '총주주환원율',
        '자본배분',
        '재무정책',
        '오버행',
        '보호예수해제',
        '락업해제',
        '의무보유해제',
        '구주매출',
        '신주모집',
        '공모구조',
        '상장주선인',
        '대표주관회사',
        '인수단',
        '인수수수료',
        '초과배정옵션',
        '그린슈옵션',
        '가격안정조치',
        '의무인수분',
        '수요예측경쟁률',
        '확약물량',
        '미확약물량',
        '균등배정수량',
        '비례배정수량',
        '배정확률',
        '청약단위',
        '청약우대조건',
        '청약수수료',
        '환불금',
        '추가납입',
        '장외파생상품',
        '금리스왑',
        '통화스왑거래',
        '총수익스왑',
        '신용부도스왑',
        '선도금리계약',
        '오버나이트인덱스스왑',
        '변동성스왑',
        '분산스왑',
        '배리어옵션',
        '디지털옵션',
        '아시아옵션',
        '스왑션',
        '캡옵션',
        '플로어옵션',
        '칼라',
        '선물환포인트',
        '스왑레이트',
        '크로스커런시베이시스',
        '달러조달비용',
        '외화자금시장',
        '역외시장',
        '역내시장',
        '국제금융시장',
        '글로벌유동성',
        '긴축금융환경',
        '완화금융환경',
        '금융여건지수',
        '실질구매력',
        '구매력평가',
        '빅맥지수',
        '임금피크제',
        '노동참가율',
        '청년실업률',
        '고용비용지수',
        '실질구매력임금',
        '단위임금비용',
        '생산성격차',
        '부가가치율',
        '투입산출표',
        '산업연관표',
        '자금순환표',
        '국민대차대조표',
        '플랫폼매출',
        '거래액',
        '총거래액',
        '순거래액',
        '테이크레이트',
        '활성이용자',
        '월간활성이용자',
        '일간활성이용자',
        '유료전환율',
        '재구매율',
        '객단가',
        '주문건수',
        '취소율',
        '환불률',
        '매출채널',
        '직판매출',
        '대리점매출',
        '해외매출',
        '내수매출',
        '제품믹스',
        '가격믹스',
        '물량효과',
        '가격효과',
        '환율효과',
        '원가절감효과',
        '영업레버리지효과',
        '변동비율',
        '고정비율',
        '기여이익률',
        '데이터드리븐랭킹',
        '랭킹실험',
        '온라인평가',
        '오프라인평가',
        '정답셋',
        '평가쿼리',
        '관련도등급',
        '평균정밀도',
        '정규화할인누적이득',
        '상호순위평균',
        '히트율',
        '커버리지율',
        '신선도점수',
        '다양성점수',
        '중복노출률',
        'alias확장률',
        '검색실패샘플',
        '오탐검색결과',
        '누락검색결과',
        '랭킹회귀',
        '랭킹가드레일',
        '사전커버리지목표',
        'alias커버리지목표',
        '채권듀레이션',
        '맥컬리듀레이션',
        '유효듀레이션',
        '컨벡시티',
        '음의컨벡시티',
        '경상수익률',
        '채권총수익률',
        '채권가격민감도',
        '수익률곡선위험',
        '캐리수익',
        '신용스프레드듀레이션',
        '제로볼래틸리티스프레드',
        '자산스왑스프레드',
        '채권스프레드',
        '크레딧베타',
        '기대손실',
        '부도시손실률',
        '부도노출액',
        '예상신용손실',
        '신용손실충당금',
        '신용등급전망',
        '신용등급감시',
        '등급상향',
        '등급하향',
        '담보회수율',
        '부실채권비율',
        '무수익여신비율',
        '여신한도',
        '대출약정한도',
        '미사용여신한도',
        '한도대출',
        '마이너스통장',
        '만기연장',
        '대환대출',
        '기한이익상실',
        '채무불이행',
        '담보권실행',
        '근저당권',
        '질권설정',
        '보증채무',
        '연대보증',
        '담보가치평가',
        '담보보전비율',
        '차주신용평가',
        '내부등급법',
        '표준방법'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '위험가중자산밀도' THEN 'It is an indicator that shows asset risk as the ratio of risk-weighted assets to total assets.'
        WHEN '여신심사' THEN 'This is a process that examines the borrower''s ability to repay, collateral business feasibility, etc. before executing a loan.'
        WHEN '사후관리' THEN 'This is a management activity that checks changes in the borrower''s credit status and collateral value after executing a loan.'
        WHEN '여신건전성분류' THEN 'It is a soundness evaluation that classifies loans into normal, precautionary, fixed, questionable, estimated loss, etc.'
        WHEN '정상여신' THEN 'This is a loan classified as having no problems with repayment ability and collectability.'
        WHEN '요주의여신' THEN 'Although it is not currently insolvent, it is a loan that requires careful recovery in the future.'
        WHEN '고정여신' THEN 'This is a loan with a significant risk of recovery and therefore the possibility of some loss.'
        WHEN '회수의문여신' THEN 'This is a loan with a very low probability of recovery, so significant losses are expected.'
        WHEN '추정손실여신' THEN 'This is a loan that is almost impossible to recover and is therefore likely to be treated as a loss.'
        WHEN '대손상각' THEN 'This is an accounting treatment that removes unrecoverable receivables from the books as a loss.'
        WHEN '채권재조정' THEN 'This is a procedure to adjust the maturity interest rate, principal, etc. to lower the debtor''s repayment burden.'
        WHEN '워크아웃' THEN 'This is a process to restructure the debt of a company or individual through consultation between a creditor financial institution and a debtor.'
        WHEN '프리워크아웃' THEN 'It is a system that adjusts debt conditions at an early stage to prevent prolonged delinquency.'
        WHEN '개인회생채권' THEN 'These are bonds that are subject to adjustment according to the repayment plan in the personal rehabilitation process.'
        WHEN '기업회생채권' THEN 'These are bonds whose rights are changed or repaid according to the rehabilitation plan in the corporate rehabilitation process.'
        WHEN '펀드기준가' THEN 'The price is calculated based on the net asset value per fund or 1,000 funds.'
        WHEN '펀드순자산' THEN 'It is the net asset amount after deducting liabilities and expenses from the fund''s total assets.'
        WHEN '펀드좌수' THEN 'This is a quantity representing the unit of fund beneficial interest.'
        WHEN '설정좌수' THEN 'This is the number of fund beneficiaries newly established through investor purchases.'
        WHEN '환매좌수' THEN 'This is the fund beneficiary for which the investor requested redemption.'
        WHEN '설정대금' THEN 'This is the amount paid to establish a new fund beneficiary right.'
        WHEN '환매대금' THEN 'This is the amount paid to the investor when the fund is redeemed.'
        WHEN '환매청구' THEN 'This is an act of requesting a sale in order to cash out the fund''s profit rights held by an investor.'
        WHEN '환매지급일' THEN 'This is the date on which the fund redemption amount is actually paid to the investor.'
        WHEN '환매연기' THEN 'This is a measure to postpone fund redemption payments due to reasons such as market closure and lack of liquidity.'
        WHEN '이익분배금' THEN 'This is the amount distributed to investors among the profits generated from fund management.'
        WHEN '분배금재투자' THEN 'This is a method of reinvesting fund distributions in the same fund rather than receiving them in cash.'
        WHEN '펀드운용보수' THEN 'This is compensation paid to the management company for fund asset management work.'
        WHEN '펀드판매보수' THEN 'This is compensation paid to the sales company for fund sales and customer management work.'
        WHEN '펀드수탁보수' THEN 'This is the remuneration paid to the trust business operator for the storage and management of fund assets.'
        WHEN '펀드사무관리보수' THEN 'This is compensation paid for office management tasks such as standard price calculation and accounting processing.'
        WHEN '선취판매수수료' THEN 'This is a sales fee charged in advance at the time of fund subscription.'
        WHEN '후취판매수수료' THEN 'It is a sales fee charged later on fund redemption or at a certain point in time.'
        WHEN '총보수비용비율' THEN 'It is an indicator that shows the total annual cost of fund operation and sales as a ratio to net assets.'
        WHEN '실질보수' THEN 'This is the cost level calculated by adding up the compensation and costs actually borne by the investor.'
        WHEN '클래스A펀드' THEN 'This is a fund class that mainly charges a front-end sales fee.'
        WHEN '클래스C펀드' THEN 'This is a fund class with a relatively higher compensation rate than the upfront fee.'
        WHEN '클래스E펀드' THEN 'This is a fund class set up for electronic sales channels such as online subscription.'
        WHEN '클래스S펀드' THEN 'This is a fund class sold through specific sales channels such as fund supermarkets.'
        WHEN '사모펀드세부구조' THEN 'It is a fund management structure that privately recruits minority investors.'
        WHEN '전문투자형사모펀드' THEN 'It is a private equity fund with relatively relaxed management regulations targeting professional investors.'
        WHEN '경영참여형사모펀드' THEN 'It is a private equity fund that increases the value of companies by acquiring shares and participating in management.'
        WHEN '펀드편입비' THEN 'This is the proportion of a specific stock or asset class among the fund''s assets.'
        WHEN '벤치마크대비성과' THEN 'It is a performance that indicates how much higher or lower the fund return is than the reference index or comparison target.'
        WHEN '운용역' THEN 'An operational manager in charge of investment decisions for funds or portfolios.'
        WHEN '위탁운용' THEN 'This is a method in which the asset owner entrusts management tasks to an external management company.'
        WHEN '일임운용' THEN 'It is a management method in which the investor delegates investment judgment and trading authority to the manager.'
        WHEN '수익자총회' THEN 'This is a meeting where fund beneficiaries decide on major decisions.'
        WHEN '신탁업자감시' THEN 'This is a function where the trust company monitors the management instructions of the collective investment company and the management of fund assets.'
        WHEN '판매회사관리' THEN 'The task is to fulfill the explanation obligations of the fund sales company and manage the sales process.'
        WHEN '포트폴리오베타' THEN 'This is the sensitivity of portfolio returns to changes in market returns.'
        WHEN '트래킹에러' THEN 'Volatility refers to how differently the portfolio return moves from the benchmark return.'
        WHEN '평균수익' THEN 'It is the average profit margin or amount for a profitable transaction or period.'
        WHEN '변동성기여도' THEN 'The extent to which a specific asset or factor contributes to the overall volatility of the portfolio.'
        WHEN '리스크패리티' THEN 'This is an investment strategy that adjusts the proportion of each asset so that the risk contribution is similar.'
        WHEN '목표변동성' THEN 'This is the preset volatility level that the portfolio wants to maintain.'
        WHEN '변동성타깃전략' THEN 'This is a strategy to maintain target volatility by adjusting the proportion of risky assets according to changes in market volatility.'
        WHEN '손실제한전략' THEN 'This is a strategy that reduces positions or applies hedging to prevent losses from exceeding a certain level.'
        WHEN '손절매기준' THEN 'This is a standard for selling when the price or loss rate reaches a certain level.'
        WHEN '익절기준' THEN 'It is a standard set to confirm profits when the target rate of return or price is reached.'
        WHEN '리밸런싱밴드' THEN 'This is the standard for readjustment if the asset proportion deviates from the allowable upper or lower range of the target proportion.'
        WHEN '포트폴리오턴오버' THEN 'This is the rate at which assets in the portfolio are replaced over a certain period of time.'
        WHEN '회전율비용' THEN 'These are costs such as commissions, taxes, market shock, etc. that arise from portfolio trading rotation.'
        WHEN '거래비용분석' THEN 'It is an analysis that measures and evaluates the explicit and implicit costs incurred during the trading process.'
        WHEN '슬리피지비용' THEN 'This is a cost incurred as a result of the difference between the intended order price and the actual execution price.'
        WHEN '유동성조정수익률' THEN 'It is an investment rate of return adjusted to reflect liquidity costs or tradability.'
        WHEN '하방위험' THEN 'It is the probability that the rate of return will be lower than the target level or 0 and the volatility of the loss.'
        WHEN '조건부VaR' THEN 'It is a risk indicator that represents the average loss of the loss section that exceeds a certain confidence level.'
        WHEN '스트레스테스트손실' THEN 'This is the expected portfolio loss when applying extreme market scenarios.'
        WHEN '시나리오손실' THEN 'This is the amount of loss estimated under certain market assumptions or event scenarios.'
        WHEN '스타일노출' THEN 'The portfolio is exposed to investment styles such as value stocks, growth stocks, large-cap stocks, and small-cap stocks.'
        WHEN '섹터노출' THEN 'This is the proportion and sensitivity of the portfolio invested in a specific industry or sector.'
        WHEN '금리노출' THEN 'The extent to which changes in interest rates affect the value of an asset, liability, or portfolio.'
        WHEN '크레딧노출' THEN 'It is the degree of exposure to changes in credit spread or default risk that affect value.'
        WHEN '유동성노출' THEN 'This is the degree of exposure to which worsening market liquidity affects trading possibilities and prices.'
        WHEN '보험료' THEN 'This is the amount paid by the policyholder to the insurance company to receive coverage.'
        WHEN '순보험료' THEN 'These are the risk insurance premiums and savings insurance premiums that correspond to the resources for insurance payment.'
        WHEN '부가보험료' THEN 'It is included in the insurance premium to cover the insurance company''s business expenses and profits.'
        WHEN '위험보험료' THEN 'This is an insurance premium calculated to cover the risk of insurance accidents such as death, disease, and accidents.'
        WHEN '저축보험료' THEN 'This is the portion of the insurance premium that is accumulated for maturity refund or reserve fund formation.'
        WHEN '책임준비금' THEN 'This is a reserve that an insurance company accumulates to fulfill future insurance payment obligations.'
        WHEN '지급준비금' THEN 'This is the amount accumulated in anticipation of insurance benefits to be paid for insurance accidents that have already occurred or been reported.'
        WHEN '미경과보험료적립금' THEN 'This is the amount saved for future liability for premiums that have not yet expired.'
        WHEN '해지환급금' THEN 'This is the amount paid to the policyholder when the insurance contract is canceled early.'
        WHEN '계약자적립금' THEN 'This is the amount accumulated for the policyholder and is used to calculate cancellation refund or maturity refund.'
        WHEN '공시이율' THEN 'This is the applied interest rate that insurance companies announce by reflecting interest rates and operating returns.'
        WHEN '예정이율' THEN 'This is the interest rate assumed in advance when calculating insurance premiums and liability reserves.'
        WHEN '최저보증이율' THEN 'This is the minimum applicable interest rate guaranteed by insurance companies to policyholders even when interest rates fall.'
        WHEN '특별계정' THEN 'It is a separate account that is managed separately from general assets in variable insurance, etc.'
        WHEN '일반계정' THEN 'This is an account through which an insurance company manages general insurance contract assets and liabilities separately from special accounts.'
        WHEN '보장성보험' THEN 'This is insurance whose main purpose is to cover risks such as death, disease, and accidents.'
        WHEN '종신보험' THEN 'This is a life insurance product that provides coverage until the insured person dies.'
        WHEN '정기보험' THEN 'This is an insurance product that covers risks such as death for a specified insurance period.'
        WHEN '연금보험' THEN 'It is an insurance product designed to receive benefits in the form of an annuity after a certain age.'
        WHEN '즉시연금' THEN 'This is a product that allows you to start receiving pension within a relatively short period of time after paying the lump sum premium.'
        WHEN '실손의료보험' THEN 'This is insurance that compensates for actual medical expenses incurred due to illness or injury according to the terms and conditions.'
        WHEN '자동차보험' THEN 'This is insurance that covers liability for damages and vehicle damage resulting from a car accident.'
        WHEN '장기손해보험' THEN 'It is a non-life insurance product that provides long-term coverage for damage such as injury, disease, and property.'
        WHEN '손해율' THEN 'It is the ratio of incurred losses to insurance premium income.'
        WHEN '보험사업비율' THEN 'It is the ratio of business expenses such as recruitment and maintenance to insurance premium income.'
        WHEN '합산비율' THEN 'It is an indicator that shows the profitability of insurance sales by adding up the loss ratio and expense ratio.'
        WHEN '지급여력비율' THEN 'It is a capital adequacy ratio that allows an insurance company to fulfill its obligation to pay insurance claims even if there are unexpected losses.'
        WHEN '위험기준자기자본' THEN 'It is a required equity capital standard that reflects the insurance company''s insurance market credit operation risks.'
        WHEN '보험금청구' THEN 'This is the process by which the policyholder or the insured requests payment of insurance money after an insurance accident occurs.'
        WHEN '보험금지급심사' THEN 'This is the process by which the insurance company confirms the payment requirements and amount of the claimed insurance claim.'
        WHEN '면책기간' THEN 'This is a period during which insurance benefits are not paid for specific accidents or diseases for a certain period of time after signing an insurance contract.'
        WHEN '감액기간' THEN 'This is the period set in the policy so that only a portion of the insurance money is paid, not the full amount.'
        WHEN '보험계약대출' THEN 'This is a loan that a policyholder receives from an insurance company within the scope of the cancellation refund.'
        WHEN '보험계약실효' THEN 'The validity of the insurance contract has been suspended due to non-payment of insurance premiums, etc.'
        WHEN '부활청약' THEN 'This is a process through which the policyholder applies to make a lapsed insurance contract valid again.'
        WHEN '계약전알릴의무' THEN 'It is the duty of the policyholder to inform the insurance company of important facts before signing up.'
        WHEN '고지의무위반' THEN 'This is an act that is problematic under the insurance contract when the policyholder fails to disclose important facts or informs them differently than the facts.'
        WHEN '보험금부지급' THEN 'It is a decision that insurance money will not be paid due to reasons for exemption or failure to meet requirements in the terms and conditions.'
        WHEN '입출금통장' THEN 'It is a basic bank account product that allows deposits and withdrawals freely.'
        WHEN '자유입출금예금' THEN 'It is a deposit and withdrawal deposit that allows deposits and withdrawals without period restrictions.'
        WHEN '보통예금' THEN 'It is a demand deposit that individuals or companies use for daily payments and fund storage.'
        WHEN '저축예금' THEN 'It is a deposit and withdrawal deposit provided for personal savings and payment convenience.'
        WHEN '자유적금' THEN 'It is a savings product that allows you to relatively freely decide the payment amount and time within a set limit.'
        WHEN '만기자동해지' THEN 'This is a processing method in which the deposit or savings is automatically canceled on the maturity date and the principal and interest are deposited.'
        WHEN '자동재예치' THEN 'This is a method of depositing the maturity principal or interest back into the same product or a designated product.'
        WHEN '세전이자' THEN 'This is the interest amount before deducting interest income tax and local income tax.'
        WHEN '세후이자' THEN 'This is the actual amount of interest received after deducting interest income tax and local income tax.'
        WHEN '우대조건' THEN 'This is a transaction condition that must be met to receive preferential interest rates or exemption from fees.'
        WHEN '우대금리쿠폰' THEN 'This is a coupon-type benefit provided so that additional interest rates can be applied when signing up for a specific product.'
        WHEN '비과세종합저축' THEN 'It is a savings system in which subscribers who meet the requirements receive tax exemption on interest income within a certain limit.'
        WHEN '예금자보호한도' THEN 'This is the combined limit of principal and interest that can be protected by each financial company under the deposit insurance system.'
        WHEN '예치기간' THEN 'This is the designated period for deposits or savings to be entrusted to a financial company.'
        WHEN '중도해지이율' THEN 'This is the interest rate applied when a deposit or savings is canceled before maturity.'
        WHEN '만기후이율' THEN 'This is the interest rate that is applied after deposit maturity until cancellation.'
        WHEN '이자지급식' THEN 'It is a product structure in which interest is paid in a set manner, such as at maturity or monthly.'
        WHEN '월복리' THEN 'It is a compound interest method that calculates the next month''s interest by adding the interest accrued each month to the principal.'
        WHEN '단리식' THEN 'This is a method of calculating interest only based on the principal amount, rather than adding accrued interest to the principal amount.'
        WHEN '복리식' THEN 'This method calculates the interest for the next period by adding the accrued interest to the principal.'
        WHEN '금리우대항목' THEN 'These are detailed conditions recognized for applying additional interest rates, such as using a salary transfer card.'
        WHEN '자동이체실적' THEN 'It can be used for preferential conditions based on the transaction history of regular automatic transfers.'
        WHEN '급여이체실적' THEN 'It is used for preferential treatment based on the performance of deposit transactions in the name of salary.'
        WHEN '카드사용실적' THEN 'This is transaction performance calculated based on the amount or number of card payments.'
        WHEN '첫거래우대' THEN 'This is a preferential benefit provided to customers who make their first transaction with the financial company.'
        WHEN '주거래우대' THEN 'This is a preferential benefit provided when the main transaction conditions are met, such as using a salary transfer card and automatic transfer.'
        WHEN '신규가입금리' THEN 'This is the interest rate applied when signing up for a new product.'
        WHEN '기본금리' THEN 'This is the basic interest rate applied to the product before any preferential conditions are reflected.'
        WHEN '최고금리' THEN 'This is the maximum interest rate that can be received when both the base interest rate and the preferential interest rate are reflected.'
        WHEN '적용금리' THEN 'This is the interest rate that is ultimately applied to actual contracts or transactions.'
        WHEN '약정이율' THEN 'This is the interest rate determined in advance in the contract or terms and conditions.'
        WHEN '세금우대저축' THEN 'It is a savings product that receives preferential tax treatment on interest income when certain requirements are met.'
        WHEN '만기지급식' THEN 'This is a method of paying deposit or savings interest all at once at maturity.'
        WHEN '월이자지급식' THEN 'Interest is paid monthly during the deposit period.'
        WHEN '예금담보대출' THEN 'This is a loan obtained by providing deposits or savings as collateral.'
        WHEN '신용대출' THEN 'This is a loan issued without collateral based on the borrower''s credit rating and income.'
        WHEN '비상금대출' THEN 'This is a credit loan product conveniently provided for small living funds needs.'
        WHEN '정책서민금융' THEN 'It is a financial product supported by policy to increase financial accessibility for low-income people with low credit.'
        WHEN '사잇돌대출' THEN 'This is a loan product provided in conjunction with a guarantee to support the funding needs of medium-credit borrowers.'
        WHEN '대출한도조회' THEN 'This is a process to check in advance the loan amount and conditions.'
        WHEN '금리산정' THEN 'This is a process to determine the loan interest rate by reflecting the borrower''s credit rating, collateral conditions, market interest rates, etc.'
        WHEN '신용점수' THEN 'It is a credit evaluation score calculated based on an individual’s financial transaction history and repayment ability.'
        WHEN '소득증빙' THEN 'This is data submitted to confirm income level during the process of signing up for a loan or financial product.'
        WHEN '재직증명' THEN 'It is a certification procedure or document to confirm the borrower’s employment status and place of work.'
        WHEN '대출실행일' THEN 'This is the date when the loan amount is actually paid or becomes available for use.'
        WHEN '대출만기일' THEN 'This is the contractual date on which the loan principal must be finally repaid.'
        WHEN '대출약정서' THEN 'It is a document that states the terms of the contract, including the loan amount, interest rate, repayment method, and collateral.'
        WHEN '전자약정' THEN 'This is a method of concluding financial product contracts using electronic documents and authentication procedures.'
        WHEN '임차보증금' THEN 'It is a deposit left by the tenant to the landlord in a lease agreement.'
        WHEN '보증기관' THEN 'A public institution or guarantee company that guarantees the payment obligation of a loan or contract.'
        WHEN '보증료율' THEN 'It is the ratio of the guarantee fee charged in exchange for providing the guarantee.'
        WHEN '보증한도' THEN 'This is the maximum amount that the guarantee agency can guarantee.'
        WHEN '보증서담보대출' THEN 'It is a loan executed using a guarantee issued by a guarantee institution as collateral.'
        WHEN '근저당설정비' THEN 'These are related costs, such as registration costs, incurred during the process of establishing a mortgage.'
        WHEN '인지세' THEN 'This is a tax levied when preparing taxable documents such as loan contracts.'
        WHEN '대출철회권' THEN 'It is the financial consumer’s right to withdraw from a loan contract within a certain period of time.'
        WHEN '금리인하요구권' THEN 'This is the right to request a reduction in interest rates from financial companies when the borrower''s credit status improves.'
        WHEN '연체가산이자' THEN 'This is additional interest charged on the amount not repaid on time.'
        WHEN '원리금상환예정액' THEN 'It is the sum of principal and interest to be paid on the scheduled repayment date.'
        WHEN '상환스케줄' THEN 'This is a schedule for repaying principal and interest during the loan period.'
        WHEN '일부상환' THEN 'This is a repayment method in which part of the loan principal is repaid before maturity.'
        WHEN '자동상환' THEN 'This is a method of repaying the loan by automatically withdrawing the repayment amount from a designated account.'
        WHEN '이자납입일' THEN 'This is the date set for payment of loan interest.'
        WHEN '상환계좌' THEN 'This is a designated account used to repay loan principal and interest.'
        WHEN '채무인수' THEN 'It is a contractual procedure in which another person takes over the debt of an existing debtor.'
        WHEN '공동명의대출' THEN 'It is a loan in which two or more holders are involved in the collateral or debt relationship.'
        WHEN '배우자동의' THEN 'This is a procedure to confirm the spouse’s consent in the process of providing collateral or executing a loan.'
        WHEN '소유권이전등기' THEN 'This is the process of recording changes in real estate ownership in the register.'
        WHEN '전입세대열람' THEN 'This is a procedure to check the status of the household moving into during a housing mortgage or jeonse-related review.'
        WHEN '신용카드' THEN 'It is a payment method in which you first pay within the credit limit granted by the card company and then pay the bill later.'
        WHEN '체크카드' THEN 'This is a card where the money is withdrawn from the linked account immediately upon payment.'
        WHEN '선불카드' THEN 'This is a card that can be used within the range of the pre-loaded amount.'
        WHEN '가족카드' THEN 'This card is issued so that family members can use the member''s credit limit together.'
        WHEN '법인카드' THEN 'This is a card issued to corporations or businesses for business expenses.'
        WHEN '후불교통카드' THEN 'This is a card function that uses the transportation fare first and then charges it along with the card fee.'
        WHEN '카드이용한도' THEN 'This is the maximum amount that can be paid with a card or use a loan service.'
        WHEN '일시불결제' THEN 'This is a payment method in which the card amount is paid all at once on the next payment date.'
        WHEN '할부결제' THEN 'This is a payment method in which the card amount is divided into several months.'
        WHEN '무이자할부' THEN 'This is a payment benefit in which no installment interest is charged during the installment period.'
        WHEN '부분무이자' THEN 'This is an installment benefit in which interest is exempted only for a portion of the installment period.'
        WHEN '할부수수료' THEN 'This is a cost in the form of a fee or interest charged by the credit card company when using installment payments.'
        WHEN '리볼빙' THEN 'This is a contract service that allows you to pay only a portion of your card payment and carry over the remainder to the next month.'
        WHEN '카드결제일' THEN 'This is the date set for payment of card usage fees.'
        WHEN '결제대금' THEN 'This is the bill amount that must be paid on the payment date after using the card.'
        WHEN '최소결제금액' THEN 'This is the minimum amount that must be paid to avoid delinquency in revolving, etc.'
        WHEN '카드연체료' THEN 'This is a fee charged when you fail to pay your card payment on time.'
        WHEN '카드론' THEN 'This is a long-term card loan provided based on the credit of the card member.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '위험가중자산밀도',
        '여신심사',
        '사후관리',
        '여신건전성분류',
        '정상여신',
        '요주의여신',
        '고정여신',
        '회수의문여신',
        '추정손실여신',
        '대손상각',
        '채권재조정',
        '워크아웃',
        '프리워크아웃',
        '개인회생채권',
        '기업회생채권',
        '펀드기준가',
        '펀드순자산',
        '펀드좌수',
        '설정좌수',
        '환매좌수',
        '설정대금',
        '환매대금',
        '환매청구',
        '환매지급일',
        '환매연기',
        '이익분배금',
        '분배금재투자',
        '펀드운용보수',
        '펀드판매보수',
        '펀드수탁보수',
        '펀드사무관리보수',
        '선취판매수수료',
        '후취판매수수료',
        '총보수비용비율',
        '실질보수',
        '클래스A펀드',
        '클래스C펀드',
        '클래스E펀드',
        '클래스S펀드',
        '사모펀드세부구조',
        '전문투자형사모펀드',
        '경영참여형사모펀드',
        '펀드편입비',
        '벤치마크대비성과',
        '운용역',
        '위탁운용',
        '일임운용',
        '수익자총회',
        '신탁업자감시',
        '판매회사관리',
        '포트폴리오베타',
        '트래킹에러',
        '평균수익',
        '변동성기여도',
        '리스크패리티',
        '목표변동성',
        '변동성타깃전략',
        '손실제한전략',
        '손절매기준',
        '익절기준',
        '리밸런싱밴드',
        '포트폴리오턴오버',
        '회전율비용',
        '거래비용분석',
        '슬리피지비용',
        '유동성조정수익률',
        '하방위험',
        '조건부VaR',
        '스트레스테스트손실',
        '시나리오손실',
        '스타일노출',
        '섹터노출',
        '금리노출',
        '크레딧노출',
        '유동성노출',
        '보험료',
        '순보험료',
        '부가보험료',
        '위험보험료',
        '저축보험료',
        '책임준비금',
        '지급준비금',
        '미경과보험료적립금',
        '해지환급금',
        '계약자적립금',
        '공시이율',
        '예정이율',
        '최저보증이율',
        '특별계정',
        '일반계정',
        '보장성보험',
        '종신보험',
        '정기보험',
        '연금보험',
        '즉시연금',
        '실손의료보험',
        '자동차보험',
        '장기손해보험',
        '손해율',
        '보험사업비율',
        '합산비율',
        '지급여력비율',
        '위험기준자기자본',
        '보험금청구',
        '보험금지급심사',
        '면책기간',
        '감액기간',
        '보험계약대출',
        '보험계약실효',
        '부활청약',
        '계약전알릴의무',
        '고지의무위반',
        '보험금부지급',
        '입출금통장',
        '자유입출금예금',
        '보통예금',
        '저축예금',
        '자유적금',
        '만기자동해지',
        '자동재예치',
        '세전이자',
        '세후이자',
        '우대조건',
        '우대금리쿠폰',
        '비과세종합저축',
        '예금자보호한도',
        '예치기간',
        '중도해지이율',
        '만기후이율',
        '이자지급식',
        '월복리',
        '단리식',
        '복리식',
        '금리우대항목',
        '자동이체실적',
        '급여이체실적',
        '카드사용실적',
        '첫거래우대',
        '주거래우대',
        '신규가입금리',
        '기본금리',
        '최고금리',
        '적용금리',
        '약정이율',
        '세금우대저축',
        '만기지급식',
        '월이자지급식',
        '예금담보대출',
        '신용대출',
        '비상금대출',
        '정책서민금융',
        '사잇돌대출',
        '대출한도조회',
        '금리산정',
        '신용점수',
        '소득증빙',
        '재직증명',
        '대출실행일',
        '대출만기일',
        '대출약정서',
        '전자약정',
        '임차보증금',
        '보증기관',
        '보증료율',
        '보증한도',
        '보증서담보대출',
        '근저당설정비',
        '인지세',
        '대출철회권',
        '금리인하요구권',
        '연체가산이자',
        '원리금상환예정액',
        '상환스케줄',
        '일부상환',
        '자동상환',
        '이자납입일',
        '상환계좌',
        '채무인수',
        '공동명의대출',
        '배우자동의',
        '소유권이전등기',
        '전입세대열람',
        '신용카드',
        '체크카드',
        '선불카드',
        '가족카드',
        '법인카드',
        '후불교통카드',
        '카드이용한도',
        '일시불결제',
        '할부결제',
        '무이자할부',
        '부분무이자',
        '할부수수료',
        '리볼빙',
        '카드결제일',
        '결제대금',
        '최소결제금액',
        '카드연체료',
        '카드론'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '현금서비스' THEN 'It is a short-term card loan service that lends cash to card members for a short period of time.'
        WHEN '단기카드대출' THEN 'This is a short-term cash loan service provided by credit card companies.'
        WHEN '장기카드대출' THEN 'It is a card-based loan provided by credit card companies on the condition of installment repayment over a certain period of time.'
        WHEN '이용대금명세서' THEN 'This is a statement that summarizes card usage details and the amount to be paid.'
        WHEN '청구할인' THEN 'This is a benefit that provides a discount on the payment amount at the billing stage after using the card.'
        WHEN '즉시할인' THEN 'This discount is applied immediately at the time of payment.'
        WHEN '캐시백' THEN 'This is a service that returns a portion of the payment amount as a cash benefit.'
        WHEN '포인트적립' THEN 'This is a benefit that accumulates points based on the amount of card usage.'
        WHEN '마일리지적립' THEN 'This is a benefit that allows you to accumulate airline mileage based on the amount of card usage.'
        WHEN '전월실적' THEN 'This is the result of adding up the card usage amount of the previous month to determine whether card benefits are provided.'
        WHEN '실적제외' THEN 'This is a usage amount item that is not included in the previous month’s performance when calculating card benefits.'
        WHEN '통합할인한도' THEN 'This is the maximum discount limit applied monthly by combining multiple discount benefits.'
        WHEN '영역별한도' THEN 'This is a benefit limit that is applied separately for each specific industry, such as transportation, communication, and shopping.'
        WHEN '해외이용수수료' THEN 'This is a fee charged when making payments at overseas merchants or using overseas ATMs.'
        WHEN '해외원화결제' THEN 'This is a method where payments are made in Korean Won rather than local currency at overseas affiliated stores.'
        WHEN '카드분실신고' THEN 'This is a procedure to request suspension of use to prevent fraudulent use when the card is lost.'
        WHEN '카드재발급' THEN 'This is the process of reissuing a card due to reasons such as loss, damage, or expiration.'
        WHEN '카드해지' THEN 'This is a procedure to terminate the card contract and prevent further use.'
        WHEN '카드정지' THEN 'This is a measure to temporarily or long-term suspend the use of the card for reasons such as risk management of loss or overdue payment.'
        WHEN '카드승인' THEN 'This is a process in which a card payment request is approved after passing the limits and transaction conditions.'
        WHEN '매입전표' THEN 'This is a payment slip received by the card company to purchase merchant transactions.'
        WHEN '승인취소' THEN 'This is the process of canceling an already approved card transaction.'
        WHEN '매출전표' THEN 'This is a slip confirming the fact that a card transaction occurred and the amount.'
        WHEN '가맹점수수료' THEN 'This is a fee paid by card merchants to card companies for card payment sales.'
        WHEN '간편결제등록' THEN 'This is the process of registering a card to connect it to a simple payment service so that payments can be made quickly.'
        WHEN '자동납부' THEN 'This is a service that automatically pays communication bills, insurance premiums, utility bills, etc. on a set date.'
        WHEN '보험나이' THEN 'This is the insurance standard age used to calculate insurance premiums and determine eligibility.'
        WHEN '보험기간' THEN 'This is the period during which coverage is provided in an insurance contract.'
        WHEN '납입기간' THEN 'This is the period within which insurance premiums are to be paid.'
        WHEN '납입주기' THEN 'This is a method that determines the frequency with which insurance premiums will be paid, such as monthly payment or annual payment.'
        WHEN '보험가입금액' THEN 'This is the contractual guaranteed amount that becomes the standard for payment in the event of an insured event.'
        WHEN '보험증권' THEN 'This is a certificate that states the main contents and coverage conditions of the insurance contract.'
        WHEN '보험약관' THEN 'It is a document that sets out the rights, obligations, coverage, exemptions, etc. of an insurance contract.'
        WHEN '청약철회' THEN 'This is a procedure that allows the policyholder to cancel the subscription within a certain period of time.'
        WHEN '품질보증해지' THEN 'This is a system that allows the contractor to cancel the contract when there are problems with the sales process, such as explanation of terms and conditions.'
        WHEN '보험계약자' THEN 'A person who enters into an insurance contract and is obligated to pay insurance premiums.'
        WHEN '피보험자' THEN 'A person who is the subject of an insurance accident.'
        WHEN '보험수익자' THEN 'This is the person who will receive the insurance money when an event occurs.'
        WHEN '주계약' THEN 'It is the central contract that constitutes the basic coverage in an insurance product.'
        WHEN '특약' THEN 'It is an additional contract added to the main contract to expand specific coverage or change the conditions.'
        WHEN '갱신형특약' THEN 'This is a special contract in which premiums and coverage conditions can be renewed at regular intervals.'
        WHEN '비갱신형특약' THEN 'It is a special contract that guarantees coverage under specified conditions without renewal during the contract period.'
        WHEN '갱신보험료' THEN 'This is an insurance premium that is recalculated at the time of renewal, taking into account age, risk rate, and loss ratio.'
        WHEN '보험료납입면제' THEN 'This is a system that exempts future insurance premium payments in the event of a specific accident or disability.'
        WHEN '감액완납보험' THEN 'This is a form of insurance that reduces the insured amount but maintains it without paying future premiums.'
        WHEN '연장정기보험' THEN 'This is a method of converting to a term insurance form with an extended insurance period using the cancellation refund.'
        WHEN '자동대출납입' THEN 'This is a system that allows insurance premiums to be automatically paid through insurance contract loans when premiums are not paid.'
        WHEN '보험료유예' THEN 'This is a system that temporarily postpones insurance premium payments under certain conditions.'
        WHEN '실효예고' THEN 'This is notice in advance that the contract may be suspended due to non-payment of insurance premiums, etc.'
        WHEN '만기보험금' THEN 'This is an insurance benefit paid according to the terms and conditions at the end of the insurance period.'
        WHEN '사망보험금' THEN 'This is an insurance benefit paid to the beneficiary when the insured person dies.'
        WHEN '진단보험금' THEN 'This is insurance money paid when a diagnosis such as cancer or cerebrovascular disease is confirmed according to the terms and conditions.'
        WHEN '입원급여금' THEN 'This is an insurance benefit paid according to the terms and conditions when the insured person is hospitalized.'
        WHEN '수술급여금' THEN 'This is insurance money paid according to the terms and conditions when the insured person undergoes surgery.'
        WHEN '장해급여금' THEN 'This is insurance money paid when you become disabled due to injury or disease.'
        WHEN '보험금청구서류' THEN 'These are documents such as medical certificates, receipts, and invoices that must be submitted for insurance payment review.'
        WHEN '손해사정' THEN 'It is a procedure to investigate and evaluate liability for compensation for damages caused by an insurance accident.'
        WHEN '자기부담금' THEN 'This is the amount that the policyholder must bear directly when paying insurance benefits.'
        WHEN '보상한도' THEN 'This is the maximum amount or range that an insurance company can compensate under its terms and conditions.'
        WHEN '통원한도' THEN 'This is the maximum amount or number of times compensated for outpatient or outpatient treatment.'
        WHEN '입원일당' THEN 'This insurance payment is paid on a daily basis depending on the number of days of hospitalization.'
        WHEN '면책사항' THEN 'This is the reason or scope for which the insurance company does not pay insurance benefits according to the terms and conditions.'
        WHEN '확정급여형' THEN 'This is a type of retirement pension in which the employer guarantees the level of retirement benefits and assumes responsibility for managing reserves.'
        WHEN '확정기여형' THEN 'This is a type of retirement pension in which the employer pays a set contribution and the employee bears the results of the operation.'
        WHEN '퇴직급여' THEN 'This is severance pay or pension benefits paid to workers when they retire.'
        WHEN '퇴직급여충당부채' THEN 'It is a liability that a company recognizes in accounting for its obligation to pay retirement benefits in the future.'
        WHEN '운용관리기관' THEN 'It is an organization that performs operation management tasks such as presenting retirement pension management products and educating subscribers.'
        WHEN '자산관리기관' THEN 'It is an organization that performs asset management tasks such as storing retirement pension reserves and carrying out management instructions.'
        WHEN '사용자부담금' THEN 'This is a contribution that users pay into their retirement pension account.'
        WHEN '가입자부담금' THEN 'This is an additional amount paid by workers or subscribers to their retirement pension account.'
        WHEN '디폴트옵션' THEN 'This is a system that manages savings using pre-designated products when the subscriber does not give operation instructions.'
        WHEN '퇴직연금펀드' THEN 'This is a fund product designed for investment in retirement pension accounts.'
        WHEN '원리금보장상품' THEN 'It is a retirement pension management product such as deposit insurance that guarantees payment of principal and agreed interest.'
        WHEN '실적배당형상품' THEN 'It is a retirement pension management product with a rate of return that varies depending on management performance.'
        WHEN '위험자산한도' THEN 'This is the maximum ratio that can be invested in risky assets such as stock funds in a retirement pension account.'
        WHEN '퇴직연금이전' THEN 'This is the process of transferring retirement pension savings or contracts to another financial company or system.'
        WHEN '연금수령기간' THEN 'This is the period during which you will receive benefits in the form of a pension.'
        WHEN '연금외수령' THEN 'This is a method of receiving a lump sum payment without meeting the pension requirements.'
        WHEN '세액공제대상금액' THEN 'This is the amount of pension account contributions that can receive tax deduction benefits.'
        WHEN '연금계좌세액공제' THEN 'This is a system that applies tax deductions to pension savings or IRP payments.'
        WHEN '납입한도초과분' THEN 'This is the amount paid in excess of the tax benefit or product limit.'
        WHEN '퇴직연금수수료' THEN 'This is a fee charged for retirement pension operation management and asset management.'
        WHEN '환전' THEN 'It is a transaction that exchanges one currency for another currency.'
        WHEN '환율우대' THEN 'This benefit provides a discount on currency exchange fees applied when exchanging currency or sending foreign currency.'
        WHEN '매매기준율' THEN 'This is the exchange rate that serves as the standard for the purchase and sale exchange rates announced by foreign exchange banks.'
        WHEN '현찰살때환율' THEN 'This is the exchange rate applied when customers purchase foreign currency cash.'
        WHEN '현찰팔때환율' THEN 'This is the exchange rate applied when a customer sells foreign currency cash.'
        WHEN '송금보낼때환율' THEN 'This is the exchange rate applied when sending foreign currency abroad.'
        WHEN '송금받을때환율' THEN 'This is the exchange rate applied when receiving foreign currency from overseas and converting it to won.'
        WHEN '외화정기예금' THEN 'It is a term deposit product that deposits foreign currency for a certain period of time and earns interest.'
        WHEN '외화보통예금' THEN 'It is a deposit product that allows free deposits and withdrawals in foreign currencies.'
        WHEN '외화송금' THEN 'This is a transaction that sends funds to a domestic or foreign account in a foreign currency.'
        WHEN '해외송금' THEN 'This is a transaction that transfers foreign currency to an overseas recipient.'
        WHEN '스위프트코드' THEN 'It is a standard code that identifies banks in international financial transactions.'
        WHEN '중계은행수수료' THEN 'This is a fee charged by an intermediate bank during the overseas remittance process.'
        WHEN '전신료' THEN 'This is a fee charged for processing wire services such as foreign currency remittance.'
        WHEN '외화수표' THEN 'A check is denominated in a foreign currency and is paid through a foreign financial institution.'
        WHEN '여행자수표' THEN 'It is a check-type payment method issued to overseas travelers to use instead of cash.'
        WHEN '환전수수료' THEN 'This is a fee charged by financial companies when exchanging currencies.'
        WHEN '계좌통합조회' THEN 'This is a service that searches account information scattered across multiple financial companies at once.'
        WHEN '자동이체' THEN 'This is a service that automatically transfers money from a designated account on a set date.'
        WHEN '예약이체' THEN 'This is a service that schedules a transfer to be carried out on a pre-specified date and amount.'
        WHEN '즉시이체' THEN 'This is a service that sends funds to the receiving account immediately after requesting a transfer.'
        WHEN '지연이체' THEN 'This is a transfer method that is carried out a certain period of time after the transfer request is made to enhance security.'
        WHEN '이체한도' THEN 'This is the maximum amount that can be transferred in one day or at one time.'
        WHEN '보안매체' THEN 'It is a means such as OTP security card certificate used for financial transaction authentication and security.'
        WHEN '공동인증서' THEN 'It is an electronic certificate using the joint authentication method used for electronic financial transactions and identity verification.'
        WHEN '금융인증서' THEN 'It is a cloud-type financial transaction certificate based on Korea Financial Telecommunications and Clearings Institute.'
        WHEN '간편인증' THEN 'This is an authentication method that easily verifies your identity using password, biometric information, civil certificate, etc.'
        WHEN '생체인증' THEN 'It is an authentication method that uses biometric information, such as fingerprints or faces, to verify one''s identity.'
        WHEN '이상거래탐지' THEN 'It is a monitoring system that detects financial transactions that differ from normal patterns and prevents accidents.'
        WHEN '전자금융사기' THEN 'It is a fraudulent act of stealing funds by abusing electronic financial transactions.'
        WHEN '보이스피싱' THEN 'It is a fraud crime that steals financial information or funds through phone calls or messages.'
        WHEN '지급정지' THEN 'This is a measure to prevent payment of an account or transaction when fraud or disputes are suspected.'
        WHEN '착오송금반환' THEN 'This is a procedure to receive a refund of money that was sent incorrectly.'
        WHEN '본인신용정보관리업' THEN 'This is an industry that provides integrated inquiry and analysis of individual credit information and management services.'
        WHEN '스크래핑' THEN 'This is a method of automatically collecting web screen information based on user consent and using it for services.'
        WHEN '오픈API' THEN 'It is an interface that allows external services to link financial functions or data in a standardized way.'
        WHEN '개별재무제표' THEN 'It is a financial statement prepared on the basis of a non-consolidated company or individual corporation.'
        WHEN '회계정책' THEN 'These are the recognition measurement and presentation standards and methods applied when preparing financial statements.'
        WHEN '회계추정' THEN 'It is a reasonable estimate to reflect an uncertain amount or period in financial statements.'
        WHEN '회계추정변경' THEN 'It is an accounting process that adjusts existing accounting estimates due to new information or changes in circumstances.'
        WHEN '회계정책변경' THEN 'It is an accounting treatment that changes the accounting policy being applied to a different accounting policy.'
        WHEN '전기오류수정' THEN 'This is an accounting process that discovers and corrects errors in financial statements from previous periods.'
        WHEN '재작성' THEN 'This is the process of rewriting past financial statements due to errors or changes in standards.'
        WHEN '비교표시' THEN 'This is a method of displaying previous period information along with current financial statements to enable comparison between periods.'
        WHEN '계속기업가정' THEN 'It is an assumption in preparing financial statements that a company will continue to operate without liquidation for the foreseeable future.'
        WHEN '중요성기준' THEN 'This is a standard for determining whether omission or distortion of financial information can affect user judgment.'
        WHEN '영업활동현금흐름' THEN 'It is the cash inflow and outflow generated from a company''s main business activities.'
        WHEN '간접법현금흐름' THEN 'This is a method of calculating operating cash flow by adjusting non-cash items and changes in working capital in net income.'
        WHEN '직접법현금흐름' THEN 'This is a method of directly displaying cash inflow and outflow from operating activities by major items.'
        WHEN '비현금거래' THEN 'It is a transaction in which assets, liabilities, and capital items change without cash inflow or outflow.'
        WHEN '운전자본변동' THEN 'It is a change in business-related assets and liabilities such as trade receivables, inventory, and accounts payable.'
        WHEN '영업운전자본' THEN 'It is a component of net working capital, such as trade receivables, inventory, and purchase payables required for business activities.'
        WHEN '반제품' THEN 'This is inventory that has partially completed the manufacturing process but requires further processing.'
        WHEN '저장품' THEN 'It is an item held for use in repairs, consumables, packaging, etc.'
        WHEN '순실현가능가치' THEN 'When selling inventory, it is the amount calculated by deducting the finished selling cost from the estimated selling price.'
        WHEN '토지' THEN 'It is a tangible asset that is not depreciated, such as land owned by a company.'
        WHEN '건물' THEN 'It is a tangible asset of a building used for business or rental.'
        WHEN '구축물' THEN 'These are tangible assets of fixed structures other than buildings, such as roads, storage tanks, and pipes.'
        WHEN '기계장치' THEN 'These are mechanical equipment assets used to produce products or provide services.'
        WHEN '차량운반구' THEN 'These are vehicles and transportation equipment used for commercial transportation management purposes.'
        WHEN '공구기구비품' THEN 'These are tangible assets such as tools and equipment used for sales or management.'
        WHEN '건설중인자산' THEN 'This is an account that capitalizes expenditures related to construction or facility acquisition that have not yet been completed.'
        WHEN '손상검사' THEN 'This is a procedure to evaluate whether the book value of an asset exceeds its recoverable amount.'
        WHEN '산업재산권' THEN 'These are intangible rights that are legally protected, such as patents, trademarks, and design rights.'
        WHEN '개발비' THEN 'This is the amount recognized as intangible assets for development stage expenditures that meet the requirements.'
        WHEN '소프트웨어자산' THEN 'It is an intangible asset recognized through the acquisition or development of software.'
        WHEN '회원권' THEN 'It is an intangible asset that represents the right to use facilities such as golf courses and condos.'
        WHEN '상각누계액' THEN 'This is the accumulated amount of amortization recognized for assets subject to amortization, such as intangible assets.'
        WHEN '상각방법' THEN 'This is a method of allocating the cost of assets, such as intangible assets, as expenses over their useful lives.'
        WHEN '단기리스' THEN 'A lease with a lease term of 12 months or less.'
        WHEN '소액자산리스' THEN 'This is a lease where simple accounting treatment can be permitted due to the low underlying asset value.'
        WHEN '판매후리스' THEN 'This is a transaction that involves selling an asset and then leasing the same asset again.'
        WHEN '금융리스' THEN 'A lease in which most of the risks and rewards of owning an asset are transferred.'
        WHEN '운용리스' THEN 'It is a form of lease that does not qualify as a financial lease.'
        WHEN '수행의무' THEN 'It is a promise to transfer distinct goods or services in a contract with a customer.'
        WHEN '거래가격' THEN 'This is the amount of consideration expected to be received for transferring goods or services to a customer.'
        WHEN '변동대가' THEN 'It is a contract consideration whose amount can change due to discounts, rebates, performance compensation, etc.'
        WHEN '고객충성제도' THEN 'It is a customer compensation program that provides future benefits such as points and mileage.'
        WHEN '본인대리인판단' THEN 'This is the process of determining whether the company is the primary responsible party or agent in revenue recognition.'
        WHEN '총액매출인식' THEN 'This is a method in which the total amount of customer consideration is recognized as sales when the company is determined to be the principal.'
        WHEN '순액매출인식' THEN 'When a company is determined to be an agent, only the commission or net amount is recognized as sales.'
        WHEN '복구충당부채' THEN 'It is a provision recognized for the obligation to remove or restore assets to their original state.'
        WHEN '소송충당부채' THEN 'This is a provision recognized when there is a high possibility of loss in ongoing litigation, etc. and the amount can be estimated.'
        WHEN '보험수리적손익' THEN 'This is a gain or loss arising from changes in assumptions or adjustments to experience in calculating retirement benefit obligations.'
        WHEN '당기근무원가' THEN 'This is the severance benefit liability that has increased due to the work services provided by the worker during the current period.'
        WHEN '이자원가' THEN 'Retirement benefit liabilities are costs that increase over time.'
        WHEN '공동기업투자' THEN 'It is an investment in a company that holds the rights to net assets according to a joint control agreement.'
        WHEN '지분법손익' THEN 'This is the amount equivalent to the investor''s share of the net profit or loss of an associate or joint enterprise.'
        WHEN '지분법자본변동' THEN 'This is the amount reflected in the capital corresponding to the investor''s share of the changes in the invested company''s capital.'
        WHEN '내부거래제거' THEN 'This is a procedure to remove intra-group transactions and bonds and liabilities when preparing consolidated financial statements.'
        WHEN '미실현손익제거' THEN 'This is a procedure to remove profits and losses that have not yet been realized externally from consolidated internal transactions.'
        WHEN '사업결합' THEN 'A transaction or event in which an acquirer acquires control of one or more businesses.'
        WHEN '취득법' THEN 'This is an accounting method that recognizes identifiable assets and liabilities at fair value in a business combination.'
        WHEN '식별가능순자산' THEN 'It is the net amount of a business combination minus liabilities from individually identifiable assets.'
        WHEN '염가매수차익' THEN 'It is a gain recognized when the acquisition consideration is less than the fair value of identifiable net assets.'
        WHEN '공정가치측정' THEN 'This is a method of measuring assets or liabilities at normal transaction prices between market participants.'
        WHEN '수준1투입변수' THEN 'It is an observable quoted price for the same asset or liability in an active market.'
        WHEN '수준2투입변수' THEN 'Except for level 1, these are valuation input variables that can be observed directly or indirectly.'
        WHEN '수준3투입변수' THEN 'It is a valuation input variable that uses company assumptions due to the lack of observable market data.'
        WHEN '금융자산상각후원가' THEN 'This is a classification that measures financial assets for the purpose of receiving contract cash flow using the effective interest rate method.'
        WHEN '당기손익공정가치금융자산' THEN 'It is a classification of financial assets in which changes in fair value are recognized as profit or loss.'
        WHEN '기타포괄손익공정가치금융자산' THEN 'It is a classification of financial assets in which changes in fair value are recognized as other comprehensive income.'
        WHEN '유효이자율법' THEN 'This is a method of calculating the amortized cost and interest income cost of financial assets or liabilities.'
        WHEN '파생상품자산' THEN 'It is an asset recognized when the fair value of a derivative contract is positive.'
        WHEN '파생상품부채' THEN 'This is a liability recognized when the fair value of a derivative contract is negative.'
        WHEN '위험회피회계' THEN 'It is an accounting treatment that adjusts the timing of profit and loss recognition of the risk hedging instrument and the hedging object.'
        WHEN '공정가치위험회피' THEN 'It is a type of hedge accounting to avoid the risk of fair value fluctuations.'
        WHEN '현금흐름위험회피' THEN 'It is a type of hedge accounting to avoid the risk of future cash flow fluctuations.'
        WHEN '해외사업장순투자위험회피' THEN 'This is an accounting treatment to avoid the risk of converting net investment in overseas business sites.'
        WHEN '당기법인세' THEN 'This is the amount of corporate tax to be paid or refunded on current taxable income.'
        WHEN '일시적차이' THEN 'It is the difference between the book value of assets and liabilities and their tax base.'
        WHEN '가산할일시적차이' THEN 'It is a temporary difference that increases future taxable income.'
        WHEN '차감할일시적차이' THEN 'It is a temporary difference that reduces future taxable income.'
        WHEN '세무상결손금' THEN 'According to tax law, this is a loss that arises when deductibles exceed profits.'
        WHEN '이월결손금' THEN 'This is a loss carried over to the next fiscal year to be deducted from future taxable income.'
        WHEN '과세소득' THEN 'This is the amount of income subject to corporate tax according to tax laws.'
        WHEN '과세표준' THEN 'This is the taxable amount calculated to apply the tax rate.'
        WHEN '산출세액' THEN 'This is the tax amount calculated by applying the tax rate to the tax base.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '현금서비스',
        '단기카드대출',
        '장기카드대출',
        '이용대금명세서',
        '청구할인',
        '즉시할인',
        '캐시백',
        '포인트적립',
        '마일리지적립',
        '전월실적',
        '실적제외',
        '통합할인한도',
        '영역별한도',
        '해외이용수수료',
        '해외원화결제',
        '카드분실신고',
        '카드재발급',
        '카드해지',
        '카드정지',
        '카드승인',
        '매입전표',
        '승인취소',
        '매출전표',
        '가맹점수수료',
        '간편결제등록',
        '자동납부',
        '보험나이',
        '보험기간',
        '납입기간',
        '납입주기',
        '보험가입금액',
        '보험증권',
        '보험약관',
        '청약철회',
        '품질보증해지',
        '보험계약자',
        '피보험자',
        '보험수익자',
        '주계약',
        '특약',
        '갱신형특약',
        '비갱신형특약',
        '갱신보험료',
        '보험료납입면제',
        '감액완납보험',
        '연장정기보험',
        '자동대출납입',
        '보험료유예',
        '실효예고',
        '만기보험금',
        '사망보험금',
        '진단보험금',
        '입원급여금',
        '수술급여금',
        '장해급여금',
        '보험금청구서류',
        '손해사정',
        '자기부담금',
        '보상한도',
        '통원한도',
        '입원일당',
        '면책사항',
        '확정급여형',
        '확정기여형',
        '퇴직급여',
        '퇴직급여충당부채',
        '운용관리기관',
        '자산관리기관',
        '사용자부담금',
        '가입자부담금',
        '디폴트옵션',
        '퇴직연금펀드',
        '원리금보장상품',
        '실적배당형상품',
        '위험자산한도',
        '퇴직연금이전',
        '연금수령기간',
        '연금외수령',
        '세액공제대상금액',
        '연금계좌세액공제',
        '납입한도초과분',
        '퇴직연금수수료',
        '환전',
        '환율우대',
        '매매기준율',
        '현찰살때환율',
        '현찰팔때환율',
        '송금보낼때환율',
        '송금받을때환율',
        '외화정기예금',
        '외화보통예금',
        '외화송금',
        '해외송금',
        '스위프트코드',
        '중계은행수수료',
        '전신료',
        '외화수표',
        '여행자수표',
        '환전수수료',
        '계좌통합조회',
        '자동이체',
        '예약이체',
        '즉시이체',
        '지연이체',
        '이체한도',
        '보안매체',
        '공동인증서',
        '금융인증서',
        '간편인증',
        '생체인증',
        '이상거래탐지',
        '전자금융사기',
        '보이스피싱',
        '지급정지',
        '착오송금반환',
        '본인신용정보관리업',
        '스크래핑',
        '오픈API',
        '개별재무제표',
        '회계정책',
        '회계추정',
        '회계추정변경',
        '회계정책변경',
        '전기오류수정',
        '재작성',
        '비교표시',
        '계속기업가정',
        '중요성기준',
        '영업활동현금흐름',
        '간접법현금흐름',
        '직접법현금흐름',
        '비현금거래',
        '운전자본변동',
        '영업운전자본',
        '반제품',
        '저장품',
        '순실현가능가치',
        '토지',
        '건물',
        '구축물',
        '기계장치',
        '차량운반구',
        '공구기구비품',
        '건설중인자산',
        '손상검사',
        '산업재산권',
        '개발비',
        '소프트웨어자산',
        '회원권',
        '상각누계액',
        '상각방법',
        '단기리스',
        '소액자산리스',
        '판매후리스',
        '금융리스',
        '운용리스',
        '수행의무',
        '거래가격',
        '변동대가',
        '고객충성제도',
        '본인대리인판단',
        '총액매출인식',
        '순액매출인식',
        '복구충당부채',
        '소송충당부채',
        '보험수리적손익',
        '당기근무원가',
        '이자원가',
        '공동기업투자',
        '지분법손익',
        '지분법자본변동',
        '내부거래제거',
        '미실현손익제거',
        '사업결합',
        '취득법',
        '식별가능순자산',
        '염가매수차익',
        '공정가치측정',
        '수준1투입변수',
        '수준2투입변수',
        '수준3투입변수',
        '금융자산상각후원가',
        '당기손익공정가치금융자산',
        '기타포괄손익공정가치금융자산',
        '유효이자율법',
        '파생상품자산',
        '파생상품부채',
        '위험회피회계',
        '공정가치위험회피',
        '현금흐름위험회피',
        '해외사업장순투자위험회피',
        '당기법인세',
        '일시적차이',
        '가산할일시적차이',
        '차감할일시적차이',
        '세무상결손금',
        '이월결손금',
        '과세소득',
        '과세표준',
        '산출세액'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '결정세액' THEN 'This is the tax amount that is finally confirmed after reflecting tax deductions, reductions, etc.'
        WHEN '납부세액' THEN 'This is the amount of tax that must actually be paid.'
        WHEN '환급세액' THEN 'This is the amount of tax that will be refunded because the amount of tax already paid is greater than the determined tax amount.'
        WHEN '세액공제' THEN 'It is a system that directly deducts a certain amount from the calculated tax amount.'
        WHEN '세액감면' THEN 'It is a system that reduces part or all of the tax amount depending on the policy purpose.'
        WHEN '소득공제' THEN 'This is a system that deducts a certain amount when calculating taxable income.'
        WHEN '익금산입' THEN 'Even if it is not accounting profit, it is an adjustment added to taxable income under tax law.'
        WHEN '익금불산입' THEN 'It is an accounting profit, but it is an adjustment excluded from taxable income under tax law.'
        WHEN '손금산입' THEN 'Even if it is not an accounting expense, it is an adjustment recognized and deducted as a tax expense.'
        WHEN '손금불산입' THEN 'Although it is an accounting expense, it is not recognized as an expense under tax law, so it is an adjustment added to taxable income.'
        WHEN '유보' THEN 'A tax difference that leaves the tax adjustment amount open for adverse adjustment in future periods.'
        WHEN '사외유출' THEN 'This is a type of income disposal as the tax adjustment amount is considered to have leaked out of the company.'
        WHEN '기타사외유출' THEN 'This is a type of disposal of income flowing outside the company where the attribution is unclear or requires separate disposal.'
        WHEN '상여처분' THEN 'This is an income disposal that treats the amount outflowed from outside the company as an employee bonus for tax purposes.'
        WHEN '배당처분' THEN 'For tax purposes, this is an income disposal that treats the outflow from the company as a shareholder dividend.'
        WHEN '기타소득처분' THEN 'This is an income disposal that considers the amount outflowed from outside the company as other income for tax purposes.'
        WHEN '부가가치세' THEN 'This is a tax levied on the added value generated during the supply of goods or services.'
        WHEN '매출세액' THEN 'This is a tax amount calculated by applying the value-added tax rate to the supply price in a sales transaction.'
        WHEN '매입세액' THEN 'This is the amount of value-added tax paid on the purchase transaction.'
        WHEN '매입세액공제' THEN 'This is a system in which the input tax paid by the business is deducted from the sales tax.'
        WHEN '불공제매입세액' THEN 'This is an input tax amount that is not allowed to be deducted under tax law.'
        WHEN '영세율' THEN 'This is a system that applies a 0% value-added tax rate to certain transactions, such as exports.'
        WHEN '면세공급' THEN 'It is the supply of goods or services that is not subject to value-added tax.'
        WHEN '간주공급' THEN 'Even if it is not an actual sale, it is a transaction that is considered a supply of goods under tax law.'
        WHEN '세금계산서' THEN 'This is a document that proves the supply value and tax amount of a transaction subject to value-added tax.'
        WHEN '전자세금계산서' THEN 'This is a tax invoice issued and transmitted electronically.'
        WHEN '수정세금계산서' THEN 'This is a tax invoice that is reissued for reasons such as correction of errors in changes in supply price.'
        WHEN '지급명세서' THEN 'This is data submitted to the tax authorities on income payment details and withholding tax amounts.'
        WHEN '투자설명서정정' THEN 'This is a disclosure that is revised to reflect changes in the contents of the securities report or investment prospectus.'
        WHEN '첨부정정' THEN 'This is a correction that corrects errors or omissions in the attached document, not the body of the disclosure.'
        WHEN '기재정정' THEN 'This is a correction that modifies the content itself stated in the disclosure document.'
        WHEN '제출인' THEN 'A corporate individual or organization that submits public disclosure documents.'
        WHEN '보고자' THEN 'It is the entity that bears reporting obligations, such as reporting stock holdings.'
        WHEN '발행회사' THEN 'A company that issues securities or is subject to public disclosure.'
        WHEN '대상회사' THEN 'It is a company that is subject to transactions such as tender offers, mergers, and stock acquisition.'
        WHEN '공시대리인' THEN 'An agent who performs disclosure-related submission tasks on behalf of the company.'
        WHEN '대표보고자' THEN 'It is the entity that submits the report as a representative in joint holding or joint reporting.'
        WHEN '최대주주등' THEN 'It is a group of shareholders including the largest shareholder and his or her specially related persons.'
        WHEN '임원주요주주보고' THEN 'This is a disclosure that reports changes in holdings of specific securities by executives or major shareholders.'
        WHEN '보유목적변경' THEN 'This is a change in the purpose of holding stocks to simple investment, general investment, and management participation.'
        WHEN '의결권있는주식' THEN 'These are stocks that can exercise voting rights at the general shareholders'' meeting.'
        WHEN '잠재주식' THEN 'These are securities that can become common stock in the future through the exercise of conversion rights, preemptive rights, etc.'
        WHEN '자기주식취득' THEN 'It is the act of purchasing own stocks issued by a company through the market or contract.'
        WHEN '자기주식처분' THEN 'It is an act of selling or giving away shares held by a company.'
        WHEN '자기주식소각' THEN 'This is the act of reducing the number of issued shares by eliminating the company''s own shares.'
        WHEN '이익소각' THEN 'This is the act of burning treasury stocks using dividendable profits.'
        WHEN '감자' THEN 'It is a procedure under the Company Act to reduce capital.'
        WHEN '현금배당' THEN 'This is a method of distributing profits to shareholders in cash.'
        WHEN '분기배당' THEN 'This is a dividend paid out on a quarterly basis.'
        WHEN '배당기준일' THEN 'This is the base date for determining which shareholders will receive dividends.'
        WHEN '구주주배정' THEN 'It is a capital increase method that allocates new shares first to existing shareholders.'
        WHEN '일반공모증자' THEN 'It is a capital increase method that solicits new shares from an unspecified number of investors.'
        WHEN '제3자배정증자' THEN 'It is a capital increase method that allocates new shares to specific investors or stakeholders.'
        WHEN '실권주일반공모' THEN 'This is a method of offering forfeited shares to general investors.'
        WHEN '초과청약' THEN 'This is the act of subscribing to a larger quantity than the allocated number of new shares.'
        WHEN '전환비율' THEN 'This is the exchange ratio applied when converting private bonds or preferred stocks into common stocks.'
        WHEN '신주인수권행사가액' THEN 'This is the price per share paid to acquire new shares when exercising new stock warrants.'
        WHEN '콜옵션부사채' THEN 'It is a private bond with a call option that allows the issuing company to purchase it early.'
        WHEN '풋옵션부사채' THEN 'It is a private bond with a put option that allows the investor to demand early repayment.'
        WHEN '사채권자집회' THEN 'This is a meeting to decide on the common interests of bondholders and changes to their rights.'
        WHEN '담보부사채' THEN 'These are corporate bonds provided as collateral.'
        WHEN '무보증사채' THEN 'These are private bonds issued on the credit of the issuing company without separate collateral or guarantee.'
        WHEN '후순위사채' THEN 'These are private bonds that are repaid later than general bonds when the issuing company is liquidated.'
        WHEN '참가적우선주' THEN 'It is a preferred stock that can participate in addition to common stock dividends in addition to preferred dividends.'
        WHEN '누적적우선주' THEN 'It is a preferred stock in which unpaid preferred dividends are accumulated over the next period.'
        WHEN '비누적적우선주' THEN 'It is a preferred stock in which unpaid preferred dividends do not accumulate to the next period.'
        WHEN '흡수합병' THEN 'It is a merger method in which one company absorbs another company to survive and the other company disappears.'
        WHEN '신설합병' THEN 'This is a merger method in which all parties involved in the merger disappear and a new company is established.'
        WHEN '주식교환' THEN 'This is an organizational reorganization in which one company acquires all of the stocks of another company and gives its own stocks in return.'
        WHEN '주식이전' THEN 'This is an organizational reorganization in which existing company shareholders receive shares of the newly established parent company.'
        WHEN '영업양수' THEN 'It is a transaction in which all or an important part of another company''s business is taken over.'
        WHEN '영업양도' THEN 'It is a transaction in which all or an important part of a company''s operations are transferred to another company.'
        WHEN '주식양수도' THEN 'It is a transaction that transfers shares of a company or management rights by buying and selling stocks.'
        WHEN '의무공개매수' THEN 'This is a system that requires a public tender offer procedure under the law when acquiring a certain amount of shares.'
        WHEN '매수가액산정' THEN 'This is a procedure for calculating the price of stocks to be purchased by a company when exercising stock purchase rights.'
        WHEN '외부평가기관' THEN 'It is an independent organization that conducts valuation of important transactions such as mergers and business transfers.'
        WHEN '평가의견서' THEN 'This is a document in which an external evaluation agency has reviewed the adequacy of transaction conditions or value calculation.'
        WHEN '실사' THEN 'This is a procedure to investigate the financial and legal business risks of the target company prior to investment, merger, or acquisition.'
        WHEN '재무실사' THEN 'This is a due diligence that reviews the target company’s financial statements, taxes, cash flow, liabilities, etc.'
        WHEN '법무실사' THEN 'This is due diligence that reviews legal risks such as contracts, litigation, licensing, and governance of the target company.'
        WHEN '우발채무실사' THEN 'This is due diligence to check the target company’s potential liabilities, including litigation, guarantee, and tax risks.'
        WHEN '기업가치평가보고서' THEN 'This report summarizes the corporate value calculation method and assumption results.'
        WHEN '할인현금흐름법' THEN 'This is a method of evaluating corporate value by converting future cash flows to present value using a discount rate.'
        WHEN '상대가치평가법' THEN 'This is a method of evaluating corporate value by using multiples of similar companies or transaction cases.'
        WHEN '거래사례비교법' THEN 'This is a method of calculating value by comparing the price multiples of similar mergers and acquisitions or equity transactions.'
        WHEN '시장접근법' THEN 'This is a method of evaluating value using market prices or comparative company multiples.'
        WHEN '수익접근법' THEN 'This is a method of evaluating value by converting future profits or cash flows into present value.'
        WHEN '자산접근법' THEN 'This is a method of evaluating corporate value based on the fair value of assets and liabilities.'
        WHEN '말기가치' THEN 'It is an amount that reflects the remaining value of the company as a present value after the explicit estimation period.'
        WHEN '매출성장률가정' THEN 'This is the growth rate assumption applied to future sales estimates.'
        WHEN '영업이익률가정' THEN 'This is a profitability assumption applied to estimating future operating profit.'
        WHEN '투자지출가정' THEN 'These are prediction assumptions about future facility investment and intangible asset investment.'
        WHEN '운전자본가정' THEN 'This is an assumption about changes in working capital such as future trade receivables, inventory assets, and purchase payables.'
        WHEN '할인율가정' THEN 'This is the discount rate assumption applied when converting future cash flows to present value.'
        WHEN '자본구조가정' THEN 'This is an assumption in the evaluation or financial model of the proportion of debt and capital.'
        WHEN '실적발표' THEN 'This is an event or material where a company discloses its quarterly or annual management performance to investors.'
        WHEN 'IR자료' THEN 'This is the company''s business financial strategy material prepared to explain to investors.'
        WHEN '컨퍼런스콜' THEN 'This is a meeting where company executives explain performance and prospects to investors and analysts and answer questions.'
        WHEN '가이던스' THEN 'This is a forecast presented by a company regarding its future sales, profit, and investment plans.'
        WHEN '가이던스상향' THEN 'This involves a company adjusting its existing performance forecast to a higher level.'
        WHEN '가이던스하향' THEN 'This involves a company adjusting its existing performance forecast to a lower level.'
        WHEN '어닝서프라이즈' THEN 'This is when actual profits significantly exceed market expectations.'
        WHEN '어닝쇼크' THEN 'This is a case where actual profits are significantly lower than market expectations.'
        WHEN '컨센서스상향' THEN 'This is a phenomenon in which analysts’ average forecasts are higher than before.'
        WHEN '컨센서스하향' THEN 'This is a phenomenon in which analysts’ average forecasts are lower than before.'
        WHEN '목표주가상향' THEN 'This is an adjustment to raise the target stock price suggested by the securities company from before.'
        WHEN '목표주가하향' THEN 'This is an adjustment that lowers the target stock price suggested by the securities company from before.'
        WHEN '투자의견상향' THEN 'This is when a securities company adjusts its investment opinion on a stock to a more positive level.'
        WHEN '투자의견하향' THEN 'This is when a securities company adjusts its investment opinion on a stock to a more negative level.'
        WHEN '리포트커버리지' THEN 'This is the scope where analysts regularly publish reports using a specific company or industry as the target of analysis.'
        WHEN '신규커버리지' THEN 'This involves an analyst starting a new analysis of a specific company.'
        WHEN '커버리지중단' THEN 'This is when an analyst stops publishing analysis reports on a specific company.'
        WHEN '탐방노트' THEN 'This is data that summarizes business status and investment points after visiting a company or meeting.'
        WHEN '실적리뷰' THEN 'This report analyzes announced performance and summarizes differences compared to expectations and future prospects.'
        WHEN '프리뷰보고서' THEN 'This is a report that presents expected performance and key points to watch before the performance announcement.'
        WHEN '미국S&P500ETF' THEN 'It is an exchange-traded fund that tracks the U.S. S&P 500 index.'
        WHEN '미국나스닥100ETF' THEN 'It is an exchange-traded fund that tracks the U.S. NASDAQ 100 index.'
        WHEN '미국다우존스ETF' THEN 'It is an exchange-traded fund that tracks the U.S. Dow Jones Industrial Average.'
        WHEN '미국러셀2000ETF' THEN 'It is an exchange-traded fund that tracks the Russell 2000 index focusing on small and mid-cap U.S. stocks.'
        WHEN '미국배당성장ETF' THEN 'It is an exchange-traded fund that invests in American companies that have steadily increased dividends.'
        WHEN '미국고배당ETF' THEN 'It is an exchange-traded fund that invests in US stocks with high dividend yields.'
        WHEN '미국테크ETF' THEN 'It is an exchange-traded fund that focuses on investing in the U.S. technology stock sector.'
        WHEN '미국반도체ETF' THEN 'It is an exchange-traded fund that invests in U.S. or global semiconductor-related companies.'
        WHEN '미국헬스케어ETF' THEN 'It is an exchange-traded fund that invests in companies in the U.S. healthcare sector.'
        WHEN '미국금융ETF' THEN 'It is an exchange-traded fund that invests in the U.S. financial stock sector.'
        WHEN '미국리츠ETF' THEN 'It is an exchange-traded fund that invests in US-listed REITs and real estate-related stocks.'
        WHEN '미국채ETF' THEN 'It is an exchange-traded fund that invests in U.S. government bonds.'
        WHEN '미국장기채ETF' THEN 'It is an exchange-traded fund that invests in long-maturity U.S. Treasury bonds.'
        WHEN '미국중기채ETF' THEN 'It is an exchange-traded fund that invests in U.S. Treasury bonds of medium-term maturity.'
        WHEN '미국단기채ETF' THEN 'It is an exchange-traded fund that invests in short-term maturity U.S. Treasury bonds.'
        WHEN '미국물가연동채ETF' THEN 'It is an exchange-traded fund that invests in U.S. inflation-linked government bonds.'
        WHEN '미국회사채ETF' THEN 'It is an exchange-traded fund that invests in U.S. corporate bonds.'
        WHEN '미국투자등급회사채ETF' THEN 'It is an exchange-traded fund that invests in investment grade U.S. corporate bonds.'
        WHEN '미국하이일드ETF' THEN 'It is an exchange-traded fund that invests in U.S. high-yield bonds.'
        WHEN '달러ETF' THEN 'It is an exchange-traded fund that tracks the value of the U.S. dollar or dollar-related assets.'
        WHEN '엔화ETF' THEN 'It is an exchange-traded fund that tracks the value of the Japanese yen or yen-related assets.'
        WHEN '유로화ETF' THEN 'It is an exchange-traded fund that tracks the value of the euro or euro-related assets.'
        WHEN '중국A주ETF' THEN 'It is an exchange-traded fund that invests in mainland China’s A-share market.'
        WHEN '중국테크ETF' THEN 'It is an exchange-traded fund that invests in Chinese technology stocks and platform companies.'
        WHEN '일본니케이ETF' THEN 'It is an exchange-traded fund that tracks Japan''s Nikkei 225 index.'
        WHEN '인도ETF' THEN 'It is an exchange-traded fund that invests in the Indian stock market.'
        WHEN '베트남ETF' THEN 'It is an exchange-traded fund that invests in the Vietnamese stock market.'
        WHEN '신흥국ETF' THEN 'It is an exchange-traded fund that diversifies investments in stocks and bonds of emerging countries.'
        WHEN '선진국ETF' THEN 'It is an exchange-traded fund that diversifies investments in stocks and bonds of developed countries.'
        WHEN '글로벌인프라ETF' THEN 'It is an exchange-traded fund that invests in global infrastructure-related companies and assets.'
        WHEN '글로벌리츠ETF' THEN 'It is an exchange-traded fund that invests in global listed REITs and real estate-related stocks.'
        WHEN '은ETF' THEN 'It is an exchange-traded fund that tracks silver prices or silver-related assets.'
        WHEN '구리ETF' THEN 'It is an exchange-traded fund that tracks copper prices or copper-related indices.'
        WHEN '농산물ETF' THEN 'It is an exchange-traded fund that tracks the price index of agricultural products such as grains.'
        WHEN '탄소배출권ETF' THEN 'It is an exchange-traded fund that tracks carbon emissions prices or related indices.'
        WHEN '버퍼ETF' THEN 'It is an exchange-traded fund with a structure that buffers a certain loss range and limits the extent of participation in upside.'
        WHEN '물리복제ETF' THEN 'It is an ETF method that tracks the index by actually owning the stocks that make up the underlying index.'
        WHEN '합성복제ETF' THEN 'It is an ETF method that tracks the performance of the underlying index using derivative contracts such as swaps.'
        WHEN '부분복제ETF' THEN 'It is an ETF method that tracks the index by selectively holding some of the stocks that make up the underlying index.'
        WHEN '완전복제ETF' THEN 'It is an ETF method that holds almost the same weight as the constituents of the underlying index.'
        WHEN 'ETF괴리율' THEN 'This is an indicator that represents the difference between the ETF market price and net asset value as a ratio.'
        WHEN 'ETF추적오차' THEN 'This is the extent to which ETF returns move differently from the underlying index returns.'
        WHEN 'ETF추적차이' THEN 'It is the difference between the ETF cumulative return and the underlying index cumulative return.'
        WHEN 'ETF분배락' THEN 'This is a phenomenon in which ETFs are traded without the right to pay dividends.'
        WHEN 'ETF보수율' THEN 'This is the cost of operating and managing the ETF expressed as a ratio to net assets.'
        WHEN 'ETF설정' THEN 'This is a procedure where a designated participating company pays the underlying assets and receives new ETF beneficiary certificates.'
        WHEN 'ETF환매' THEN 'This is the process of returning ETF beneficiary certificates in the form of underlying assets or cash.'
        WHEN '지정참가회사' THEN 'Market participants, such as securities companies, are responsible for setting up and repurchasing ETFs.'
        WHEN '추정순자산가치' THEN 'This value is calculated by estimating the real-time net asset value of the ETF during the day.'
        WHEN '지수리밸런싱' THEN 'It is a procedure to adjust the constituent stocks or weight of the basic index according to established rules.'
        WHEN '주가지수선물' THEN 'It is a futures contract that uses a stock index as the underlying asset.'
        WHEN '통화선물' THEN 'It is a futures contract with a foreign currency as the underlying asset.'
        WHEN '금선물' THEN 'It is a futures contract with gold as the underlying asset.'
        WHEN '원유선물' THEN 'It is a futures contract with crude oil as the underlying asset.'
        WHEN '구리선물' THEN 'It is a futures contract with copper as the underlying asset.'
        WHEN '천연가스선물' THEN 'It is a futures contract with natural gas as the underlying asset.'
        WHEN '농산물선물' THEN 'It is a futures contract that uses agricultural products such as grains as the underlying asset.'
        WHEN '선물증거금' THEN 'This is the amount of collateral deposited to guarantee the performance of a futures contract.'
        WHEN '개시증거금' THEN 'This is the initial margin required when opening a new futures or options position.'
        WHEN '증거금콜' THEN 'This is a request from a financial company or exchange to make up for the margin shortfall.'
        WHEN '최종결제' THEN 'It is a procedure to settle contractual rights and obligations in cash or in kind when a derivative product expires.'
        WHEN '만기월' THEN 'This is the month in which a futures or options contract expires.'
        WHEN '원월물' THEN 'It is a futures contract with a relatively distant maturity.'
        WHEN '상품스프레드' THEN 'It is a spread that uses differences in futures prices of related raw materials or products.'
        WHEN '크랙스프레드' THEN 'It is an indicator related to refining margin that represents the difference between the price of crude oil and the price of refined products.'
        WHEN '크러시스프레드' THEN 'It is a product spread that utilizes the price difference of processed products such as soybeans, soybean oil, and soybean meal.'
        WHEN '베이시스거래' THEN 'It is an arbitrage transaction or hedging transaction that utilizes the difference between spot and futures prices.'
        WHEN '차익거래프로그램' THEN 'It is a program trading that automatically trades using the difference between spot and futures prices.'
        WHEN '비차익프로그램' THEN 'It is a program transaction that buys and sells a basket of index constituents without being directly linked to futures.'
        WHEN '행사가격' THEN 'This is the standard price for buying and selling the underlying asset when exercising the option right.'
        WHEN '내가격옵션' THEN 'It is an option that has economic value if exercised immediately.'
        WHEN '등가격옵션' THEN 'This is an option where the underlying asset price and exercise price are almost the same.'
        WHEN '외가격옵션' THEN 'It is an option that has no economic value even if exercised immediately.'
        WHEN '옵션만기' THEN 'This is the date or time when the option right expires.'
        WHEN '행사일' THEN 'This is the date on which the option right can be exercised or exercised.'
        WHEN '자동행사' THEN 'This is a procedure in which options that meet the conditions at expiration are automatically exercised without a separate application.'
        WHEN '시간가치' THEN 'The value of the option price is based on the time remaining until expiration and the possibility of volatility.'
        WHEN '로' THEN 'Option price sensitivity to interest rate changes.'
        WHEN '델타헤지' THEN 'It is a hedging strategy that adjusts portfolio delta to neutral or target level.'
        WHEN '감마스퀴즈' THEN 'This is a phenomenon in which demand for option position hedging causes greater price movements of the underlying asset.'
        WHEN '변동성스마일' THEN 'This is a phenomenon where the option implied volatility by exercise price shows a smile-shaped curve.'
        WHEN '변동성스큐' THEN 'The implied volatility by option exercise price is asymmetrically tilted.'
        WHEN '풋콜패리티' THEN 'This is the theoretical price relationship between call options, put options, underlying assets, and risk-free bonds.'
        WHEN '풋콜비율' THEN 'It is a ratio comparing put option trading volume or open interest to call option.'
        WHEN '내재변동성지수' THEN 'It is an index of future volatility expectations reflected in option prices.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '결정세액',
        '납부세액',
        '환급세액',
        '세액공제',
        '세액감면',
        '소득공제',
        '익금산입',
        '익금불산입',
        '손금산입',
        '손금불산입',
        '유보',
        '사외유출',
        '기타사외유출',
        '상여처분',
        '배당처분',
        '기타소득처분',
        '부가가치세',
        '매출세액',
        '매입세액',
        '매입세액공제',
        '불공제매입세액',
        '영세율',
        '면세공급',
        '간주공급',
        '세금계산서',
        '전자세금계산서',
        '수정세금계산서',
        '지급명세서',
        '투자설명서정정',
        '첨부정정',
        '기재정정',
        '제출인',
        '보고자',
        '발행회사',
        '대상회사',
        '공시대리인',
        '대표보고자',
        '최대주주등',
        '임원주요주주보고',
        '보유목적변경',
        '의결권있는주식',
        '잠재주식',
        '자기주식취득',
        '자기주식처분',
        '자기주식소각',
        '이익소각',
        '감자',
        '현금배당',
        '분기배당',
        '배당기준일',
        '구주주배정',
        '일반공모증자',
        '제3자배정증자',
        '실권주일반공모',
        '초과청약',
        '전환비율',
        '신주인수권행사가액',
        '콜옵션부사채',
        '풋옵션부사채',
        '사채권자집회',
        '담보부사채',
        '무보증사채',
        '후순위사채',
        '참가적우선주',
        '누적적우선주',
        '비누적적우선주',
        '흡수합병',
        '신설합병',
        '주식교환',
        '주식이전',
        '영업양수',
        '영업양도',
        '주식양수도',
        '의무공개매수',
        '매수가액산정',
        '외부평가기관',
        '평가의견서',
        '실사',
        '재무실사',
        '법무실사',
        '우발채무실사',
        '기업가치평가보고서',
        '할인현금흐름법',
        '상대가치평가법',
        '거래사례비교법',
        '시장접근법',
        '수익접근법',
        '자산접근법',
        '말기가치',
        '매출성장률가정',
        '영업이익률가정',
        '투자지출가정',
        '운전자본가정',
        '할인율가정',
        '자본구조가정',
        '실적발표',
        'IR자료',
        '컨퍼런스콜',
        '가이던스',
        '가이던스상향',
        '가이던스하향',
        '어닝서프라이즈',
        '어닝쇼크',
        '컨센서스상향',
        '컨센서스하향',
        '목표주가상향',
        '목표주가하향',
        '투자의견상향',
        '투자의견하향',
        '리포트커버리지',
        '신규커버리지',
        '커버리지중단',
        '탐방노트',
        '실적리뷰',
        '프리뷰보고서',
        '미국S&P500ETF',
        '미국나스닥100ETF',
        '미국다우존스ETF',
        '미국러셀2000ETF',
        '미국배당성장ETF',
        '미국고배당ETF',
        '미국테크ETF',
        '미국반도체ETF',
        '미국헬스케어ETF',
        '미국금융ETF',
        '미국리츠ETF',
        '미국채ETF',
        '미국장기채ETF',
        '미국중기채ETF',
        '미국단기채ETF',
        '미국물가연동채ETF',
        '미국회사채ETF',
        '미국투자등급회사채ETF',
        '미국하이일드ETF',
        '달러ETF',
        '엔화ETF',
        '유로화ETF',
        '중국A주ETF',
        '중국테크ETF',
        '일본니케이ETF',
        '인도ETF',
        '베트남ETF',
        '신흥국ETF',
        '선진국ETF',
        '글로벌인프라ETF',
        '글로벌리츠ETF',
        '은ETF',
        '구리ETF',
        '농산물ETF',
        '탄소배출권ETF',
        '버퍼ETF',
        '물리복제ETF',
        '합성복제ETF',
        '부분복제ETF',
        '완전복제ETF',
        'ETF괴리율',
        'ETF추적오차',
        'ETF추적차이',
        'ETF분배락',
        'ETF보수율',
        'ETF설정',
        'ETF환매',
        '지정참가회사',
        '추정순자산가치',
        '지수리밸런싱',
        '주가지수선물',
        '통화선물',
        '금선물',
        '원유선물',
        '구리선물',
        '천연가스선물',
        '농산물선물',
        '선물증거금',
        '개시증거금',
        '증거금콜',
        '최종결제',
        '만기월',
        '원월물',
        '상품스프레드',
        '크랙스프레드',
        '크러시스프레드',
        '베이시스거래',
        '차익거래프로그램',
        '비차익프로그램',
        '행사가격',
        '내가격옵션',
        '등가격옵션',
        '외가격옵션',
        '옵션만기',
        '행사일',
        '자동행사',
        '시간가치',
        '로',
        '델타헤지',
        '감마스퀴즈',
        '변동성스마일',
        '변동성스큐',
        '풋콜패리티',
        '풋콜비율',
        '내재변동성지수'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '변동성선물' THEN 'It is a futures contract based on a volatility index or volatility index.'
        WHEN '거래승수' THEN 'This is the unit that is multiplied when converting the price of a derivative product into the actual contract amount.'
        WHEN '계약단위' THEN 'Futures option is the quantity or amount of the underlying asset represented by one contract.'
        WHEN '비농업고용' THEN 'This is an indicator of the change in the number of employees excluding the U.S. agricultural sector.'
        WHEN '실업수당청구건수' THEN 'It is an employment indicator that represents the number of people who have newly claimed unemployment benefits in the United States.'
        WHEN 'ISM제조업지수' THEN 'This is an indicator of the U.S. manufacturing economy through a survey of purchasing managers.'
        WHEN 'ISM서비스업지수' THEN 'This is an indicator of the economic situation of the U.S. service industry through a survey of purchasing managers.'
        WHEN '미시간소비자심리지수' THEN 'It is an indicator of American consumer sentiment regarding the economy and consumption outlook.'
        WHEN '컨퍼런스보드소비자신뢰지수' THEN 'It is a trust indicator calculated by examining American consumers’ current economic evaluation and expectations.'
        WHEN 'PCE물가지수' THEN 'It is a price index that represents changes in the price of personal consumption expenditures in the United States.'
        WHEN '근원PCE' THEN 'This is the US personal consumption expenditure price index excluding food and energy.'
        WHEN '미국CPI' THEN 'It is a representative price index that measures changes in U.S. consumer prices.'
        WHEN '미국PPI' THEN 'It is a price index that measures changes in selling prices of U.S. producers.'
        WHEN '미국소매판매' THEN 'It is a consumption indicator that represents changes in sales at U.S. retailers.'
        WHEN '내구재주문' THEN 'This is an indicator of changes in new orders for durable goods that can be used for more than three years.'
        WHEN '신규주택판매' THEN 'It is a housing indicator that represents the number of newly built homes sold in the United States.'
        WHEN '기존주택판매' THEN 'It is a housing indicator that represents the number of existing housing transactions in the United States.'
        WHEN '주택착공' THEN 'It is a housing economic indicator that indicates the number of new housing construction starts.'
        WHEN '건축허가' THEN 'It is an indicator of the number of building permits that shows the possibility of future housing construction.'
        WHEN '산업생산' THEN 'It is an indicator of changes in production in industrial sectors such as manufacturing, mining, and utilities.'
        WHEN '설비가동률' THEN 'This ratio indicates how much production facilities are actually being utilized.'
        WHEN '베이지북' THEN 'This is a report published by the U.S. Federal Reserve that summarizes economic trends by region.'
        WHEN 'FOMC의사록' THEN 'This is a record of discussions at the U.S. Federal Open Market Committee meeting.'
        WHEN '점도표' THEN 'This is a chart showing the Federal Reserve members’ outlook for future policy interest rates with dots.'
        WHEN '연방기금금리' THEN 'This is the standard interest rate applied to ultra-short-term financial transactions between U.S. banks.'
        WHEN '고용보고서' THEN 'This is a report that compiles labor market indicators such as the number of employees, unemployment rate, and wages.'
        WHEN 'ECB기준금리' THEN 'This is the main policy interest rate set by the European Central Bank in its monetary policy.'
        WHEN '예금금리정책금리' THEN 'This is the policy interest rate applied to central bank deposits.'
        WHEN '유로존CPI' THEN 'It is a price index that indicates changes in consumer prices in the Eurozone.'
        WHEN '유로존PMI' THEN 'It is an indicator of economic trends based on a survey of Eurozone purchasing managers.'
        WHEN '독일IFO지수' THEN 'It is an economic indicator calculated by examining the economic evaluation and expectations of German companies.'
        WHEN '중국PMI' THEN 'This is a survey indicator for Chinese manufacturing or service industry purchasing managers.'
        WHEN '중국사회융자총량' THEN 'It is a comprehensive indicator of the amount of financing provided to China''s real economy.'
        WHEN '중국MLF금리' THEN 'This is a policy interest rate applied through the People''s Bank of China''s medium-term liquidity support window.'
        WHEN '중국LPR' THEN 'This is China''s preferential lending rate and serves as the standard for bank lending rates.'
        WHEN '일본단칸지수' THEN 'It is an economic indicator released by the Bank of Japan after examining corporate economic judgment.'
        WHEN '일본수익률곡선통제' THEN 'It is a policy system that the Bank of Japan manages to target short- and long-term interest rate levels.'
        WHEN 'BOJ정책금리' THEN 'This is the policy interest rate set by the Bank of Japan to operate monetary policy.'
        WHEN '달러인덱스' THEN 'It is an index of the value of the U.S. dollar against a basket of major currencies.'
        WHEN '캐리트레이드' THEN 'This is a strategy to raise funds with low-interest currencies and invest in high-interest assets.'
        WHEN '엔캐리트레이드' THEN 'This is a strategy to raise funds at low interest rates in the yen and invest in relatively high-yield assets.'
        WHEN '선물환율' THEN 'This is the exchange rate agreed to be applied to currency exchange at a specific point in the future.'
        WHEN '현물환율' THEN 'This is the current exchange rate applied to foreign exchange spot transactions.'
        WHEN '역외차액결제선물환' THEN 'It is a forward exchange transaction in which only the difference is settled without exchanging the principal in the offshore market.'
        WHEN '역외원달러NDF' THEN 'It is a won-dollar difference settlement forward exchange traded in the offshore market.'
        WHEN '통화베이시스' THEN 'It is a spread that reflects the difference in procurement supply and demand between the two currencies in the currency swap market.'
        WHEN '커버드이자율평가' THEN 'It is a theory that explains the balance relationship between two currency interest rates and forward exchange rates on the premise of foreign exchange hedging.'
        WHEN '언커버드이자율평가' THEN 'This is a theory that explains the relationship between the difference in interest rates between two currencies and expected exchange rate changes without currency hedging.'
        WHEN 'WTI원유' THEN 'It is crude oil produced in West Texas, USA, and is one of the representative standards for international crude oil prices.'
        WHEN '브렌트유' THEN 'It is North Sea crude oil and is one of the representative standards for international crude oil prices.'
        WHEN '두바이유' THEN 'It is one of the standard oil types that represents the price of Middle Eastern crude oil.'
        WHEN '천연가스' THEN 'It is a gaseous energy raw material used as fuel for power generation, heating, and industrial purposes.'
        WHEN '액화천연가스' THEN 'It is an energy product that liquefies natural gas to make it easy to transport and store.'
        WHEN '은현물' THEN 'It is a silver asset traded in the spot market.'
        WHEN '구리현물' THEN 'It is a copper asset traded in the spot market.'
        WHEN '알루미늄' THEN 'It is a non-ferrous metal raw material widely used in industrial products and transportation packaging.'
        WHEN '니켈' THEN 'It is a non-ferrous metal raw material used in stainless steel and battery materials.'
        WHEN '리튬' THEN 'It is a metal raw material used as a secondary battery material.'
        WHEN '철광석' THEN 'It is a mineral raw material that is the main raw material for steel production.'
        WHEN '유연탄' THEN 'Coal is a product used as power generation and industrial fuel.'
        WHEN '옥수수선물' THEN 'It is an agricultural product futures contract with corn as the underlying asset.'
        WHEN '대두선물' THEN 'It is an agricultural product futures contract with soybeans as the underlying asset.'
        WHEN '소맥선물' THEN 'It is an agricultural product futures contract with wheat as the underlying asset.'
        WHEN '원당선물' THEN 'It is a commodity futures contract that uses raw sugar, a sugar raw material, as the underlying asset.'
        WHEN '커피선물' THEN 'It is a commodity futures contract that uses coffee beans as the underlying asset.'
        WHEN '코코아선물' THEN 'It is a commodity futures contract with cocoa as the underlying asset.'
        WHEN '벌크선운임지수' THEN 'It is a shipping market index that indicates the level of dry bulk carrier freight rates.'
        WHEN '컨테이너운임지수' THEN 'It is a shipping market index that indicates the level of container ship freight rates.'
        WHEN '상하이컨테이너운임지수' THEN 'This is an index indicating the level of container freight rates from Shanghai.'
        WHEN '운임선물' THEN 'It is a derivative product traded based on the sea freight index.'
        WHEN '정제마진' THEN 'This is the theoretical margin obtained when refining crude oil and selling petroleum products.'
        WHEN '아시아정제마진' THEN 'It is a refining margin calculated as the difference between crude oil and petroleum product prices in the Asian region.'
        WHEN '원유재고' THEN 'It is an energy supply and demand indicator that indicates the level of crude oil storage.'
        WHEN '휘발유재고' THEN 'It is an energy supply and demand indicator that indicates the level of gasoline storage.'
        WHEN '시장폭' THEN 'It is a concept that indicates the strength of the overall market by the spread of rising and falling stocks.'
        WHEN '상승종목비율' THEN 'This is the percentage of stocks whose prices have risen among all stocks.'
        WHEN '하락종목비율' THEN 'This is the percentage of stocks whose prices have fallen among all stocks.'
        WHEN '신고가신저가비율' THEN 'It is a market breadth indicator that shows the relationship between the number of stocks with new high prices and the number of stocks with new low prices.'
        WHEN 'ADR지표' THEN 'It is an indicator of market overheating and recession that uses the ratio of the number of rising stocks to the number of falling stocks.'
        WHEN '등락비율' THEN 'It is a ratio comparing the number of rising stocks to the number of falling stocks.'
        WHEN '거래대금회전율' THEN 'It is a ratio that indicates how actively the transaction amount is rotating compared to the market capitalization.'
        WHEN '시가총액회전율' THEN 'It is an indicator that shows the speed of market rotation by comparing transaction amount or trading volume to market capitalization.'
        WHEN '숏커버링' THEN 'This is the act of buying back stocks to liquidate short selling positions.'
        WHEN '숏스퀴즈' THEN 'This is a phenomenon where rapid redemption by short-selling investors accelerates the price rise.'
        WHEN '롱숏전략' THEN 'This is a strategy that seeks profit by purchasing assets that are expected to rise and selling assets that are expected to fall.'
        WHEN '롱온리전략' THEN 'It is an investment strategy that focuses on buying positions without short selling.'
        WHEN '시장중립전략' THEN 'It is a strategy that reduces exposure to market direction and pursues profits from relative value or stock selection.'
        WHEN '전략적자산배분' THEN 'This is a method of determining the standard asset proportion according to long-term goals and risk tolerance.'
        WHEN '전술적자산배분' THEN 'This is a method of adjusting the proportion of assets in the short term according to the market outlook.'
        WHEN '안전자산선호' THEN 'As uncertainty increases, demand for safe assets such as government bonds, dollars, and gold is increasing.'
        WHEN '위험자산선호' THEN 'As the economy and risk preference improve, demand for risky assets such as stocks and high-yield bonds is increasing.'
        WHEN '유동성프리미엄' THEN 'It is the additional rate of return required in return for holding illiquid assets.'
        WHEN '인플레이션프리미엄' THEN 'This is the additional rate of return required for the uncertainty of future inflation.'
        WHEN '실질수익률' THEN 'It is the rate of return minus the inflation rate from the nominal rate of return.'
        WHEN '명목수익률' THEN 'It is the ostensible rate of return on investment without adjusting for changes in prices.'
        WHEN '손익분기인플레이션' THEN 'It is an indicator of expected inflation estimated as the difference between nominal government bond yields and inflation-indexed government bond yields.'
        WHEN '신용위험프리미엄' THEN 'It is the additional rate of return required in return for bearing credit risk.'
        WHEN '주식위험프리미엄' THEN 'It is the additional expected rate of return required for stock investment compared to risk-free assets.'
        WHEN '변동성위험프리미엄' THEN 'It is the risk compensation observed in the difference between realized volatility and option implied volatility.'
        WHEN '모멘텀팩터' THEN 'It is an investment factor that takes advantage of the recent tendency for assets with good returns to continue to strengthen.'
        WHEN '가치팩터' THEN 'This is an investment factor that suggests that undervalued stocks can generate excess returns in the long term.'
        WHEN '퀄리티팩터' THEN 'It is an investment factor that favors companies with high profitability, financial soundness, and profit stability.'
        WHEN '저변동성팩터' THEN 'It is an investment factor that suggests that stocks with low volatility can produce good performance relative to risk.'
        WHEN '소형주팩터' THEN 'This is an investment factor that suggests small-cap stocks can generate excess returns over the long term.'
        WHEN '배당팩터' THEN 'This is an investment factor that favors stocks with high dividend yield or dividend growth characteristics.'
        WHEN '팩터로테이션' THEN 'It is a strategy to change preferred investment factors depending on the market situation.'
        WHEN '멀티팩터전략' THEN 'This is a strategy that combines several investment factors to form a portfolio.'
        WHEN '스마트베타' THEN 'It is a rules-based factor index investment method rather than the traditional market capitalization weighting method.'
        WHEN '동일가중지수' THEN 'It is an index calculated by giving equal weight to its constituent stocks.'
        WHEN '시가총액가중지수' THEN 'It is an index calculated according to the proportion of market capitalization of constituent stocks.'
        WHEN '가격가중지수' THEN 'It is an index whose weight is determined in proportion to the prices of its constituent stocks.'
        WHEN '유동주식비율' THEN 'It is the ratio of stocks that are freely tradable in the market among the total issued stocks.'
        WHEN '유동시가총액' THEN 'It is the market capitalization calculated by multiplying the number of floating shares by the stock price.'
        WHEN '편입예상수급' THEN 'This is expected purchase demand due to index inclusion or rebalancing.'
        WHEN '편출예상수급' THEN 'This is expected selling demand due to index deletion or rebalancing.'
        WHEN '비트코인현물ETF' THEN 'It is an exchange-traded fund designed to track the Bitcoin spot price.'
        WHEN '비트코인선물ETF' THEN 'It is an exchange-traded fund operated based on Bitcoin futures contracts.'
        WHEN '이더리움현물ETF' THEN 'It is an exchange-traded fund designed to track the spot price of Ethereum.'
        WHEN '가상자산거래소' THEN 'It is a trading platform that provides trading and storage services for virtual assets.'
        WHEN '스테이블코인' THEN 'It is a virtual asset designed to be linked to fiat currency or asset prices.'
        WHEN '중앙은행디지털화폐' THEN 'It is a digital form of legal currency issued by the central bank.'
        WHEN '토큰증권' THEN 'It is a security that expresses security rights in the form of a digital token using distributed ledger technology.'
        WHEN '증권형토큰공개' THEN 'It is an act of recruiting investors by issuing security tokens.'
        WHEN '가상자산수탁' THEN 'It is a service that safely stores and manages virtual assets.'
        WHEN '디지털자산커스터디' THEN 'It is a consignment service that provides storage and transfer control of digital assets.'
        WHEN '온체인데이터' THEN 'This refers to public data such as transaction address balances recorded on the blockchain.'
        WHEN '해시레이트' THEN 'It is an indicator of the computational processing ability of the blockchain network.'
        WHEN '스테이킹' THEN 'This is the act of depositing virtual assets and receiving compensation to participate in blockchain verification.'
        WHEN '토큰소각' THEN 'This is an act of reducing supply by permanently removing some of the tokens in circulation.'
        WHEN '가상자산공시' THEN 'This is the act of disclosing the issuance volume, distribution volume, and financial business information of a virtual asset project to investors.'
        WHEN '선진국주가지수' THEN 'It is an index that represents the stock market trends of developed countries such as the United States, Europe, and Japan.'
        WHEN '신흥국주가지수' THEN 'It is an index that comprehensively represents the trends of stock markets in emerging countries.'
        WHEN '프론티어마켓' THEN 'Although the market size and liquidity are smaller than those of emerging countries, it is a financial market with growth potential.'
        WHEN '글로벌국채지수' THEN 'It is a bond index that combines the performance of the government bond markets of several countries.'
        WHEN '글로벌회사채지수' THEN 'It is a bond index that combines the performance of corporate bond markets in several countries.'
        WHEN '신흥국채권지수' THEN 'It is an index that synthesizes the performance of bonds issued by governments or companies in emerging countries.'
        WHEN '현지통화채권' THEN 'It is a bond whose principal and interest are denominated and paid in the local currency of the issuing country.'
        WHEN '글로벌하이일드채권' THEN 'It is a bond asset class that invests in global speculative grade corporate bonds.'
        WHEN '신흥국달러채권' THEN 'These are bonds issued by emerging country issuers in U.S. dollars.'
        WHEN '신흥국현지통화채권' THEN 'These are bonds issued by emerging country issuers in their own currency.'
        WHEN '소버린채권' THEN 'These are bonds issued by a country or government.'
        WHEN '준정부채권' THEN 'These are bonds issued by government agencies or public institutions and linked to government credit.'
        WHEN '국채입찰' THEN 'This is the process by which the government receives investor bids to issue government bonds.'
        WHEN '응찰률' THEN 'It is the ratio of the bid amount to the expected issuance amount in bond bidding.'
        WHEN '낙찰금리' THEN 'This is the interest rate finally decided on issuance in the bond auction.'
        WHEN '장단기금리역전' THEN 'The yield curve is in a state where short-term interest rates are higher than long-term interest rates.'
        WHEN '불스티프닝' THEN 'This is a phenomenon in which short-term interest rates fall more than long-term interest rates during a falling interest rate phase, causing the yield curve to steepen.'
        WHEN '베어플래트닝' THEN 'In a phase of rising interest rates, short-term interest rates rise more than long-term interest rates, flattening the yield curve.'
        WHEN '금리상방위험' THEN 'There is a risk that interest rates will rise more than expected in the future, which will have a detrimental effect on asset value or financing costs.'
        WHEN '금리하방위험' THEN 'There is a risk that interest rates will fall more than expected in the future, which will have a detrimental effect on returns or interest income.'
        WHEN '듀레이션오버웨이트' THEN 'It is a management position that has higher interest rate sensitivity than the standard portfolio.'
        WHEN '듀레이션언더웨이트' THEN 'It is a management position that has lower interest rate sensitivity than the reference portfolio.'
        WHEN '커브스티프너' THEN 'It is an interest rate position that expects profits when the yield curve steepens.'
        WHEN '커브플래트너' THEN 'It is an interest rate position that expects profits when the yield curve flattens.'
        WHEN '인플레이션스왑' THEN 'It is a derivative contract that exchanges the inflation rate and a fixed interest rate.'
        WHEN '실질금리채권' THEN 'It is a bond whose principal or interest is linked to prices and reflects real purchasing power.'
        WHEN '물가채브레이크이븐' THEN 'It is an indicator of expected inflation calculated as the difference between the yields on inflation-linked bonds and nominal bonds.'
        WHEN '원자재슈퍼사이클' THEN 'This is a phase in which raw material prices are structurally rising for a long period of time.'
        WHEN '에너지스프레드' THEN 'It is a spread that represents the price difference between energy raw materials.'
        WHEN '가솔린크랙' THEN 'It is a refining margin indicator calculated as the difference between crude oil and gasoline prices.'
        WHEN '디젤크랙' THEN 'It is a refining margin indicator calculated as the difference between crude oil and diesel prices.'
        WHEN '정제설비가동률' THEN 'This is the rate at which oil refinery facilities are actually operating.'
        WHEN '원유생산량' THEN 'It is the amount of crude oil produced over a certain period of time.'
        WHEN '원유수요전망' THEN 'This is the outlook for future crude oil consumption.'
        WHEN '원유공급전망' THEN 'This is the outlook for future crude oil production and supply.'
        WHEN 'OPEC감산' THEN 'This is a measure taken by the Organization of Petroleum Exporting Countries and others to reduce crude oil production.'
        WHEN 'OPEC증산' THEN 'This is a measure by the Organization of Petroleum Exporting Countries and others to increase crude oil production.'
        WHEN '전략비축유' THEN 'This is an inventory of crude oil that the government reserves to respond to the energy crisis.'
        WHEN '탄소선물' THEN 'It is a futures contract that uses carbon emissions rights as the underlying asset.'
        WHEN '전력선물' THEN 'It is a futures contract that uses the price of electricity as the underlying asset.'
        WHEN '재생에너지인증서' THEN 'It is a certificate that proves renewable energy production performance and allows for trading.'
        WHEN '해상운임스프레드' THEN 'It is a spread that represents the difference in sea freight rates by transportation route or line.'
        WHEN '항공화물운임' THEN 'This is the fare level applied to air cargo transportation.'
        WHEN '반도체현물가격' THEN 'This is the spot transaction price of major semiconductor products such as memory semiconductors.'
        WHEN '메모리가격지수' THEN 'It is an index of the price trend of memory semiconductors such as DRAM NAND.'
        WHEN '디램현물가격' THEN 'This is the spot market transaction price of DRAM products.'
        WHEN '낸드현물가격' THEN 'This is the spot market transaction price of NAND products.'
        WHEN '패널가격' THEN 'This is the market transaction price of the display panel.'
        WHEN '스마트폰출하량' THEN 'It is an industry indicator that represents the number of smartphones shipped over a certain period of time.'
        WHEN 'PC출하량' THEN 'It is an industry indicator that represents the number of personal computers shipped over a certain period of time.'
        WHEN '자동차판매대수' THEN 'It is an industrial demand indicator that represents the number of cars sold over a certain period of time.'
        WHEN '전기차침투율' THEN 'This is the percentage of electric vehicles in total vehicle sales or number of vehicles in operation.'
        WHEN '배터리출하량' THEN 'This is the capacity or quantity of secondary batteries shipped over a certain period of time.'
        WHEN '양극재가격' THEN 'This is the market price of secondary battery cathode materials.'
        WHEN '음극재가격' THEN 'This is the market price of secondary battery anode materials.'
        WHEN '탄산리튬가격' THEN 'This is the market price of lithium carbonate, which is used as a battery material.'
        WHEN '수산화리튬가격' THEN 'This is the market price of lithium hydroxide, which is used as a battery material.'
        WHEN '니켈매트' THEN 'It is a nickel intermediate material used in manufacturing battery materials.'
        WHEN '재고순환' THEN 'It is a concept that judges the economic and industrial cycle through changes in shipments and inventory.'
        WHEN '출하증가율' THEN 'It is a ratio that indicates how much product shipments have increased over a certain period of time.'
        WHEN '재고출하비율' THEN 'It is an indicator that compares inventory levels with shipments to determine supply and demand balance.'
        WHEN '공장가동률' THEN 'This is the rate at which factory equipment is utilized for actual production.'
        WHEN '수주취소' THEN 'This is an incident where an order that has already been secured is cancelled.'
        WHEN '프로젝트파이낸싱' THEN 'It is a financial structure that uses the cash flow of a specific project as the main source of repayment.'
        WHEN '사모대출펀드' THEN 'It is a fund that invests in corporate loans or private bonds.'
        WHEN '부동산대체투자' THEN 'It is a type of alternative investment that invests in real estate or related financial products.'
        WHEN '인프라대체투자' THEN 'It is a type of alternative investment that invests in infrastructure assets or projects.'
        WHEN '사모신용' THEN 'It is a credit asset class that invests in corporate loans and private bonds in the private market.'
        WHEN '세컨더리펀드' THEN 'This is a fund that purchases existing fund shares or unlisted investment shares.'
        WHEN '공동투자' THEN 'It is an investment method that involves additional participation in specific transactions with major investors.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '변동성선물',
        '거래승수',
        '계약단위',
        '비농업고용',
        '실업수당청구건수',
        'ISM제조업지수',
        'ISM서비스업지수',
        '미시간소비자심리지수',
        '컨퍼런스보드소비자신뢰지수',
        'PCE물가지수',
        '근원PCE',
        '미국CPI',
        '미국PPI',
        '미국소매판매',
        '내구재주문',
        '신규주택판매',
        '기존주택판매',
        '주택착공',
        '건축허가',
        '산업생산',
        '설비가동률',
        '베이지북',
        'FOMC의사록',
        '점도표',
        '연방기금금리',
        '고용보고서',
        'ECB기준금리',
        '예금금리정책금리',
        '유로존CPI',
        '유로존PMI',
        '독일IFO지수',
        '중국PMI',
        '중국사회융자총량',
        '중국MLF금리',
        '중국LPR',
        '일본단칸지수',
        '일본수익률곡선통제',
        'BOJ정책금리',
        '달러인덱스',
        '캐리트레이드',
        '엔캐리트레이드',
        '선물환율',
        '현물환율',
        '역외차액결제선물환',
        '역외원달러NDF',
        '통화베이시스',
        '커버드이자율평가',
        '언커버드이자율평가',
        'WTI원유',
        '브렌트유',
        '두바이유',
        '천연가스',
        '액화천연가스',
        '은현물',
        '구리현물',
        '알루미늄',
        '니켈',
        '리튬',
        '철광석',
        '유연탄',
        '옥수수선물',
        '대두선물',
        '소맥선물',
        '원당선물',
        '커피선물',
        '코코아선물',
        '벌크선운임지수',
        '컨테이너운임지수',
        '상하이컨테이너운임지수',
        '운임선물',
        '정제마진',
        '아시아정제마진',
        '원유재고',
        '휘발유재고',
        '시장폭',
        '상승종목비율',
        '하락종목비율',
        '신고가신저가비율',
        'ADR지표',
        '등락비율',
        '거래대금회전율',
        '시가총액회전율',
        '숏커버링',
        '숏스퀴즈',
        '롱숏전략',
        '롱온리전략',
        '시장중립전략',
        '전략적자산배분',
        '전술적자산배분',
        '안전자산선호',
        '위험자산선호',
        '유동성프리미엄',
        '인플레이션프리미엄',
        '실질수익률',
        '명목수익률',
        '손익분기인플레이션',
        '신용위험프리미엄',
        '주식위험프리미엄',
        '변동성위험프리미엄',
        '모멘텀팩터',
        '가치팩터',
        '퀄리티팩터',
        '저변동성팩터',
        '소형주팩터',
        '배당팩터',
        '팩터로테이션',
        '멀티팩터전략',
        '스마트베타',
        '동일가중지수',
        '시가총액가중지수',
        '가격가중지수',
        '유동주식비율',
        '유동시가총액',
        '편입예상수급',
        '편출예상수급',
        '비트코인현물ETF',
        '비트코인선물ETF',
        '이더리움현물ETF',
        '가상자산거래소',
        '스테이블코인',
        '중앙은행디지털화폐',
        '토큰증권',
        '증권형토큰공개',
        '가상자산수탁',
        '디지털자산커스터디',
        '온체인데이터',
        '해시레이트',
        '스테이킹',
        '토큰소각',
        '가상자산공시',
        '선진국주가지수',
        '신흥국주가지수',
        '프론티어마켓',
        '글로벌국채지수',
        '글로벌회사채지수',
        '신흥국채권지수',
        '현지통화채권',
        '글로벌하이일드채권',
        '신흥국달러채권',
        '신흥국현지통화채권',
        '소버린채권',
        '준정부채권',
        '국채입찰',
        '응찰률',
        '낙찰금리',
        '장단기금리역전',
        '불스티프닝',
        '베어플래트닝',
        '금리상방위험',
        '금리하방위험',
        '듀레이션오버웨이트',
        '듀레이션언더웨이트',
        '커브스티프너',
        '커브플래트너',
        '인플레이션스왑',
        '실질금리채권',
        '물가채브레이크이븐',
        '원자재슈퍼사이클',
        '에너지스프레드',
        '가솔린크랙',
        '디젤크랙',
        '정제설비가동률',
        '원유생산량',
        '원유수요전망',
        '원유공급전망',
        'OPEC감산',
        'OPEC증산',
        '전략비축유',
        '탄소선물',
        '전력선물',
        '재생에너지인증서',
        '해상운임스프레드',
        '항공화물운임',
        '반도체현물가격',
        '메모리가격지수',
        '디램현물가격',
        '낸드현물가격',
        '패널가격',
        '스마트폰출하량',
        'PC출하량',
        '자동차판매대수',
        '전기차침투율',
        '배터리출하량',
        '양극재가격',
        '음극재가격',
        '탄산리튬가격',
        '수산화리튬가격',
        '니켈매트',
        '재고순환',
        '출하증가율',
        '재고출하비율',
        '공장가동률',
        '수주취소',
        '프로젝트파이낸싱',
        '사모대출펀드',
        '부동산대체투자',
        '인프라대체투자',
        '사모신용',
        '세컨더리펀드',
        '공동투자'
  );

UPDATE dictionary
SET description_en = CASE term
        WHEN '블라인드펀드' THEN 'It is a fund in which funds are raised without determining the investment target in advance, and then the manager discovers investment destinations.'
        WHEN '프로젝트펀드' THEN 'It is a fund that raises funds for a specific investment target or project.'
        WHEN '캐피털콜' THEN 'This is a procedure in which the fund requests the investor to pay the required amount of the agreed investment amount.'
        WHEN '약정총액' THEN 'This is the total amount that fund investors have agreed to invest.'
        WHEN '미인출약정' THEN 'This is the amount that has not yet been paid through capital calls out of the total contract amount.'
        WHEN '분배가능재원' THEN 'It is a source of cash or profit that a fund or company can distribute to investors.'
        WHEN '청산분배' THEN 'This is a procedure in which the remaining assets are distributed to investors when a fund or company is liquidated.'
        WHEN 'SCL' THEN 'It is a characteristic line that estimates beta and alpha by regressing stock returns to market returns.'
        WHEN 'SML' THEN 'This is a stock market line that shows the relationship between beta and expected return in CAPM.'
        WHEN 'Ledoit-Wolf' THEN 'It is a covariance reduction estimation method that reduces estimation errors by stabilizing sample covariance.'
        WHEN '표본 공분산' THEN 'It is an estimate of the covariance between assets calculated only from the observed return sample.'
        WHEN '안정화 공분산' THEN 'This is a covariance to which reduction estimation, etc. has been applied to reduce the noise of sample covariance.'
        WHEN '자본배분선' THEN 'It is a line that connects the risk-free asset and the maximum Sharp portfolio and represents the combination of risk and expected return.'
        WHEN '투자자 무차별곡선' THEN 'It is a curve that connects a combination of volatility and expected return that gives the same utility.'
        WHEN '최대샤프 포트폴리오' THEN 'It is the risky asset combination with the highest excess return relative to risk on the efficient frontier.'
        WHEN '효용최대 포트폴리오' THEN 'This is the portfolio with the greatest utility when it reflects the investor''s risk aversion and constraints.'
        WHEN '이론적 효용접점' THEN 'This is the theoretical target point where the capital allocation line and the investor indifference curve touch when the constraints are released.'
        WHEN '위험회피계수' THEN 'It is a utility calculation input value that indicates how burdensome the investor views volatility.'
        WHEN '위험자산' THEN 'It is an investment asset that has the possibility of loss due to fluctuations in price and yield.'
        WHEN '무위험자산' THEN 'It is an asset that serves as a reference rate of return with very low volatility and credit risk.'
        WHEN '공통 표본' THEN 'This is a data section in which two return time series are observed together on the same date.'
        WHEN '결정계수' THEN 'It is an indicator that shows how much the regression model explains the variation in the dependent variable.'
        WHEN 'CAPM 반영 비중' THEN 'It is a reliability-based ratio that indicates how much the CAPM estimate will be reflected in calculating expected return.'
        WHEN 'CAPM 기대수익률' THEN 'It is the expected rate of return estimated by the risk-free rate, market risk premium, and beta.'
        WHEN '과거 흐름 추정값' THEN 'This is an expected return estimate calculated based on past return data.'
        WHEN '결합 기대수익률' THEN 'It is an optimization input value that mixes past flow estimates and CAPM expected returns according to reliability.'
        WHEN '포트폴리오 최적화' THEN 'This is the process of calculating the proportion of assets considering expected returns, risks, and constraints.'
        WHEN '유사종목' THEN 'It is a stock used as a comparison target because its price flow and industry characteristics are similar to the reference stock.'
        WHEN '유사종목 평균 흐름' THEN 'It is a baseline that summarizes the relative price trends of selected similar stocks by averaging them.'
        WHEN '유사종목 분포 범위' THEN 'This is the upper and lower range where the relative price trends of similar stocks spread.'
        WHEN '유사종목 데이터 커버리지' THEN 'This is a ratio that indicates how much similar stock price data fills the analysis period.'
        WHEN '기준 종목 수익률' THEN 'It is the period return rate of the stock that is the focus of comparative analysis.'
        WHEN '동행' THEN 'It is a relationship in which the object of comparison moves in a similar direction at the same time as the reference stock.'
        WHEN '선행' THEN 'This is a relationship in which the movement of the object of comparison appears before the reference stock.'
        WHEN '후행' THEN 'This is a relationship in which the movement of the target of comparison appears later than that of the reference stock.'
    END
WHERE (description_en IS NULL OR TRIM(description_en) = '')
  AND term IN (
        '블라인드펀드',
        '프로젝트펀드',
        '캐피털콜',
        '약정총액',
        '미인출약정',
        '분배가능재원',
        '청산분배',
        'SCL',
        'SML',
        'Ledoit-Wolf',
        '표본 공분산',
        '안정화 공분산',
        '자본배분선',
        '투자자 무차별곡선',
        '최대샤프 포트폴리오',
        '효용최대 포트폴리오',
        '이론적 효용접점',
        '위험회피계수',
        '위험자산',
        '무위험자산',
        '공통 표본',
        '결정계수',
        'CAPM 반영 비중',
        'CAPM 기대수익률',
        '과거 흐름 추정값',
        '결합 기대수익률',
        '포트폴리오 최적화',
        '유사종목',
        '유사종목 평균 흐름',
        '유사종목 분포 범위',
        '유사종목 데이터 커버리지',
        '기준 종목 수익률',
        '동행',
        '선행',
        '후행'
  );
