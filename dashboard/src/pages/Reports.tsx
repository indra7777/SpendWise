import { useState, useEffect } from 'react'
import { Calendar, TrendingUp, TrendingDown, FileText, Download, Sparkles } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'

interface Report {
  type: string
  periodStart: number
  periodEnd: number
  totalSpent: number
  transactionCount: number
  averageTransaction: number
  categoryBreakdown: { category: string; amount: number; count: number }[]
  topMerchants: { merchantName: string; total: number; count: number }[]
}

const CATEGORY_COLORS: Record<string, string> = {
  FOOD: '#f97316',
  GROCERIES: '#22c55e',
  TRANSPORT: '#3b82f6',
  SHOPPING: '#ec4899',
  UTILITIES: '#8b5cf6',
  ENTERTAINMENT: '#f43f5e',
  HEALTH: '#14b8a6',
  TRANSFERS: '#6366f1',
  OTHER: '#6b7280',
}

const REPORT_TYPES = [
  { id: 'daily', label: 'Daily', icon: '📅' },
  { id: 'weekly', label: 'Weekly', icon: '📊' },
  { id: 'monthly', label: 'Monthly', icon: '📈' },
  { id: 'quarterly', label: 'Quarterly', icon: '📉' },
  { id: 'half-yearly', label: 'Half-Yearly', icon: '📋' },
  { id: 'yearly', label: 'Annual', icon: '🎯' },
]

export default function Reports() {
  const [selectedType, setSelectedType] = useState('monthly')
  const [report, setReport] = useState<Report | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    fetchReport(selectedType)
  }, [selectedType])

  const fetchReport = async (type: string) => {
    setLoading(true)
    try {
      const res = await fetch(`/api/reports/${type}`)
      const json = await res.json()
      if (json.success) {
        setReport(json.data)
      }
    } catch (err) {
      console.error('Failed to fetch report', err)
    } finally {
      setLoading(false)
    }
  }

  const formatPeriod = (start: number, end: number) => {
    const startDate = new Date(start)
    const endDate = new Date(end)
    return `${startDate.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })} - ${endDate.toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: 'numeric' })}`
  }

  return (
    <div className="space-y-6 pb-20 md:pb-0">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-bold text-gray-900">Reports</h2>
        <button className="flex items-center space-x-2 text-primary-500 hover:text-primary-600">
          <Download size={20} />
          <span className="hidden sm:inline">Export</span>
        </button>
      </div>

      {/* Report Type Selector */}
      <div className="grid grid-cols-3 md:grid-cols-6 gap-2">
        {REPORT_TYPES.map((type) => (
          <button
            key={type.id}
            onClick={() => setSelectedType(type.id)}
            className={`p-3 rounded-xl text-center transition-all ${
              selectedType === type.id
                ? 'bg-primary-500 text-white shadow-lg'
                : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'
            }`}
          >
            <span className="text-xl">{type.icon}</span>
            <p className="text-xs font-medium mt-1">{type.label}</p>
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-500"></div>
        </div>
      ) : report ? (
        <>
          {/* Period Header */}
          <div className="card bg-gradient-to-r from-gray-800 to-gray-900 text-white">
            <div className="flex items-center space-x-2 text-gray-400 mb-2">
              <Calendar size={16} />
              <span className="text-sm">{formatPeriod(report.periodStart, report.periodEnd)}</span>
            </div>
            <p className="text-3xl font-bold">₹{report.totalSpent.toLocaleString()}</p>
            <div className="flex items-center space-x-4 mt-3 text-sm">
              <span>{report.transactionCount} transactions</span>
              <span>•</span>
              <span>Avg: ₹{report.averageTransaction.toFixed(0)}</span>
            </div>
          </div>

          {/* Category Chart */}
          <div className="card">
            <h3 className="font-semibold text-gray-900 mb-4">Spending by Category</h3>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={report.categoryBreakdown} layout="vertical">
                <XAxis type="number" tickFormatter={(value) => `₹${value}`} />
                <YAxis type="category" dataKey="category" width={100} tick={{ fontSize: 12 }} />
                <Tooltip
                  formatter={(value: number) => [`₹${value.toLocaleString()}`, 'Amount']}
                  contentStyle={{ borderRadius: '8px' }}
                />
                <Bar dataKey="amount" radius={[0, 4, 4, 0]}>
                  {report.categoryBreakdown.map((entry, index) => (
                    <Cell key={index} fill={CATEGORY_COLORS[entry.category] || '#6b7280'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Top Merchants */}
          <div className="card">
            <h3 className="font-semibold text-gray-900 mb-4">Top Merchants</h3>
            <div className="space-y-4">
              {report.topMerchants.slice(0, 5).map((merchant, index) => (
                <div key={merchant.merchantName} className="flex items-center">
                  <span className="w-6 h-6 bg-gray-100 rounded-full flex items-center justify-center text-xs font-medium text-gray-600 mr-3">
                    {index + 1}
                  </span>
                  <div className="flex-1">
                    <div className="flex justify-between items-center mb-1">
                      <span className="font-medium text-gray-900">{merchant.merchantName}</span>
                      <span className="font-semibold">₹{merchant.total.toLocaleString()}</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div
                        className="bg-primary-500 h-2 rounded-full"
                        style={{
                          width: `${(merchant.total / report.topMerchants[0].total) * 100}%`
                        }}
                      ></div>
                    </div>
                    <p className="text-xs text-gray-500 mt-1">{merchant.count} transactions</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* AI Insights Placeholder */}
          <div className="card bg-gradient-to-r from-blue-50 to-indigo-50 border-blue-100">
            <div className="flex items-center space-x-2 mb-3">
              <Sparkles className="text-blue-500" size={20} />
              <h3 className="font-semibold text-gray-900">AI Insights</h3>
            </div>
            <p className="text-gray-600 text-sm">
              Enable AI insights to get personalized recommendations based on your spending patterns.
            </p>
            <button className="mt-4 bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors">
              Generate Insights
            </button>
          </div>
        </>
      ) : (
        <div className="card text-center py-12">
          <FileText className="mx-auto text-gray-400 mb-4" size={48} />
          <p className="text-gray-500">No data available for this period</p>
        </div>
      )}
    </div>
  )
}
